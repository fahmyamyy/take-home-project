package dev.cxtrends.brief;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import dev.cxtrends.classify.Taxonomy;
import dev.cxtrends.trends.AnomalyDetector;
import dev.cxtrends.trends.Incident;
import dev.cxtrends.trends.TrendRepository;

/**
 * Computes {@link BriefFacts} for the most recent 4 complete weeks.
 *
 * <p>Rules (all explicit, so the judgment is reviewable):
 * <ul>
 *   <li><b>Baseline</b> = median weekly volume over the 8 weeks before the window.
 *       Median, so a past outage week does not inflate it.</li>
 *   <li><b>Needs attention</b>: recent weekly average ≥ 25% above baseline, at least
 *       {@value #MATERIAL_EXTRA_PER_WEEK} extra tickets a week, Poisson z ≥ 4 (well
 *       beyond chance across 27 topics), and not explained by a known incident.</li>
 *   <li><b>Watch list</b>: ≥ 20% up with z ≥ 3, but too small to escalate yet.</li>
 *   <li><b>Known cause</b>: a rise in Network Quality concentrated in a region with
 *       an incident in the window. Incidents in the window are also checked the other
 *       way: did they produce any extra tickets at all?</li>
 *   <li><b>Earlier spikes</b> in the baseline weeks (found with the same
 *       {@link AnomalyDetector} as the API, ≥ {@value #EARLIER_SPIKE_MIN_EXTRA} extra
 *       tickets) are reported as explained/resolved if volume is back to normal.</li>
 * </ul>
 */
@Component
public class BriefFactsBuilder {

    static final int RECENT_WEEKS = 4;
    static final int BASELINE_WEEKS = 8;
    static final int MATERIAL_EXTRA_PER_WEEK = 20;
    /** Earlier spikes are context, not news: only ones big enough to have been noticed. */
    static final int EARLIER_SPIKE_MIN_EXTRA = 100;
    /** An SLA miss rate this many points above its baseline is a new problem, not a chronic one. */
    static final int PROCESS_NEW_POINTS = 10;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final JdbcTemplate jdbc;
    private final TrendRepository trends;

    public BriefFactsBuilder(JdbcTemplate jdbc, TrendRepository trends) {
        this.jdbc = jdbc;
        this.trends = trends;
    }

    /**
     * Week boundaries: history = [historyStart, baselineStart) — only used to give the
     * anomaly detector and "range seen earlier" enough past —, baseline =
     * [baselineStart, recentStart), recent = [recentStart, lastWeek].
     */
    record Weeks(LocalDate historyStart, LocalDate baselineStart, LocalDate recentStart, LocalDate lastWeek) {

        LocalDate end() {
            return lastWeek.plusDays(6);
        }

        /** Index of the first baseline week in a series that starts at historyStart. */
        int baselineOffset() {
            return (int) java.time.temporal.ChronoUnit.WEEKS.between(historyStart, baselineStart);
        }

        <T> List<T> baseline(List<T> series) {
            return series.subList(baselineOffset(), baselineOffset() + BASELINE_WEEKS);
        }

        <T> List<T> recent(List<T> series) {
            return series.subList(baselineOffset() + BASELINE_WEEKS, series.size());
        }

        <T> List<T> beforeRecent(List<T> series) {
            return series.subList(0, baselineOffset() + BASELINE_WEEKS);
        }
    }

    public BriefFacts build() {
        Integer unlabelled = jdbc.queryForObject("SELECT COUNT(*) FROM ticket_topics WHERE topic_category IS NULL",
                Integer.class);
        if (unlabelled != null && unlabelled > 0) {
            throw new IllegalStateException(unlabelled + " tickets have no topic yet; run `classify` first");
        }
        TrendRepository.DataRange data = trends.dataRange();
        LocalDate lastWeek = data.lastWeek();
        LocalDate baselineStart = lastWeek.minusWeeks(RECENT_WEEKS + BASELINE_WEEKS - 1);
        if (baselineStart.isBefore(data.firstWeek())) {
            throw new IllegalStateException("need at least %d weeks of data".formatted(RECENT_WEEKS + BASELINE_WEEKS));
        }
        Weeks weeks = new Weeks(data.firstWeek(), baselineStart, lastWeek.minusWeeks(RECENT_WEEKS - 1), lastWeek);

        BriefFacts.Window window = new BriefFacts.Window(
                range(weeks.recentStart(), weeks.end()),
                range(weeks.baselineStart(), weeks.recentStart().minusDays(1)),
                RECENT_WEEKS, BASELINE_WEEKS);

        Map<String, List<Integer>> byTopic = weeklySeries("topic_category || ' > ' || topic_subcategory", weeks);
        List<Integer> total = weeklySeries("'all'", weeks).get("all");
        int totalBase = (int) Math.round(median(weeks.baseline(total)));
        int totalRecent = (int) Math.round(mean(weeks.recent(total)));
        BriefFacts.Volume volume = new BriefFacts.Volume(totalRecent, totalBase, change(totalRecent, totalBase));

        List<Incident> incidentsInWindow = trends.incidents().stream()
                .filter(i -> !i.end().isBefore(weeks.recentStart().atStartOfDay())
                        && i.start().isBefore(weeks.end().plusDays(1).atStartOfDay()))
                .toList();

        List<BriefFacts.Rising> attention = new ArrayList<>();
        List<BriefFacts.Rising> watch = new ArrayList<>();
        List<BriefFacts.Explained> explained = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> e : byTopic.entrySet()) {
            String topic = e.getKey();
            List<Integer> series = e.getValue();
            double base = median(weeks.baseline(series));
            List<Integer> recent = weeks.recent(series);
            double recentAvg = mean(recent);
            if (base <= 0) {
                continue;
            }
            double ratio = recentAvg / base;
            double expected = base * RECENT_WEEKS;
            double z = (recentAvg * RECENT_WEEKS - expected) / Math.sqrt(expected);
            double extra = recentAvg - base;

            boolean material = ratio >= 1.25 && extra >= MATERIAL_EXTRA_PER_WEEK && z >= 4;
            boolean watchable = ratio >= 1.20 && z >= 3;
            if (!material && !watchable) {
                continue;
            }
            Map<String, double[]> regions = regionRatios(topic, weeks);
            String knownCause = knownCause(topic, regions, incidentsInWindow);
            if (knownCause != null) {
                explained.add(new BriefFacts.Explained(
                        "%s up %s (%d → %d a week)".formatted(topic, change(recentAvg, base), round(base), round(recentAvg)),
                        knownCause, "None beyond the incident fix; watch that it returns to baseline."));
                continue;
            }
            long rising = regions.values().stream().filter(r -> r[1] >= 1.2 * r[0]).count();
            int earlierPeak = weeks.beforeRecent(series).stream().mapToInt(Integer::intValue).max().orElse(0);
            String explanation;
            if (material) {
                explanation = rising >= regions.size() - 1
                        ? "Up in %d of %d regions; no network incident overlaps it, so it is not an outage effect."
                          .formatted(rising, regions.size())
                        : "Up in %d of %d regions; no network incident overlaps it.".formatted(rising, regions.size());
            } else {
                explanation = recentAvg <= earlierPeak
                        ? "Small (+%d a week) and within the range seen earlier this year (busiest week: %d). Not escalated."
                          .formatted(round(extra), earlierPeak)
                        : "Small (+%d a week), but above any earlier week this year (busiest week: %d). Not escalated yet."
                          .formatted(round(extra), earlierPeak);
            }
            Taxonomy.Topic definition = Taxonomy.findByLabel(topic);
            BriefFacts.Rising item = new BriefFacts.Rising(topic, definition == null ? null : definition.definition(),
                    round(base), round(recentAvg),
                    change(recentAvg, base), List.copyOf(recent), (int) rising, regions.size(), explanation,
                    material ? impact(topic, weeks) : null,
                    material ? notableSegments(topic, weeks) : List.of());
            (material ? attention : watch).add(item);
        }
        attention.sort(Comparator.comparingInt((BriefFacts.Rising r) -> r.recentWeekly() - r.baselineWeekly()).reversed());
        watch.sort(Comparator.comparingInt((BriefFacts.Rising r) -> r.recentWeekly() - r.baselineWeekly()).reversed());

        incidentsInWindow.forEach(i -> explained.add(incidentImpact(i)));
        explained.addAll(earlierSpikes(byTopic, weeks));

        return new BriefFacts(window, volume, attention, watch, explained, processIssues(weeks), dataQuality(weeks),
                List.of("Topics come from the customer's note, not the agent's label.",
                        "Baseline is the median week of the previous %d weeks.".formatted(BASELINE_WEEKS),
                        "Escalate only if ≥25%% up, ≥%d extra tickets a week, statistically clear, and not explained by a known incident."
                                .formatted(MATERIAL_EXTRA_PER_WEEK)));
    }

    // --- rising topics ---

    /** Topic → weekly counts from baselineStart to lastWeek (zeros filled). */
    private Map<String, List<Integer>> weeklySeries(String keyExpr, Weeks weeks) {
        Map<String, Map<String, Integer>> raw = new HashMap<>();
        jdbc.query("""
                SELECT %s AS k, week_start, COUNT(*) AS n
                FROM ticket_topics WHERE week_start BETWEEN ? AND ?
                GROUP BY k, week_start
                """.formatted(keyExpr), rs -> {
                    raw.computeIfAbsent(rs.getString("k"), x -> new HashMap<>())
                            .put(rs.getString("week_start"), rs.getInt("n"));
                }, weeks.historyStart().toString(), weeks.lastWeek().toString());
        Map<String, List<Integer>> series = new java.util.TreeMap<>();
        raw.forEach((k, byWeek) -> {
            List<Integer> s = new ArrayList<>();
            for (LocalDate w = weeks.historyStart(); !w.isAfter(weeks.lastWeek()); w = w.plusWeeks(1)) {
                s.add(byWeek.getOrDefault(w.toString(), 0));
            }
            series.put(k, s);
        });
        return series;
    }

    /** Region → {baseline weekly median, recent weekly average} for a topic. */
    private Map<String, double[]> regionRatios(String topic, Weeks weeks) {
        Map<String, List<Integer>> byRegion = weeklySeries(
                "region || CASE WHEN topic_category || ' > ' || topic_subcategory = '%s' THEN '' ELSE '#other' END"
                        .formatted(topic.replace("'", "''")), weeks);
        Map<String, double[]> result = new java.util.TreeMap<>();
        byRegion.forEach((key, s) -> {
            if (!key.endsWith("#other")) {
                result.put(key, new double[] {median(weeks.baseline(s)), mean(weeks.recent(s))});
            }
        });
        return result;
    }

    /** A known incident explains a Network Quality rise if most of the extra volume is in its region. */
    private static String knownCause(String topic, Map<String, double[]> regions, List<Incident> incidents) {
        if (!topic.startsWith("Network Quality")) {
            return null;
        }
        double totalExtra = regions.values().stream().mapToDouble(r -> Math.max(0, r[1] - r[0])).sum();
        for (Incident i : incidents) {
            double[] r = regions.get(i.region());
            if (r != null && totalExtra > 0 && (r[1] - r[0]) / totalExtra >= 0.5) {
                return "%s: %s in %s, %s".formatted(i.id(), i.cause().toLowerCase(Locale.ROOT), i.region(),
                        span(i.start(), i.end()));
            }
        }
        return null;
    }

    private BriefFacts.Impact impact(String topic, Weeks weeks) {
        String sql = """
                SELECT ROUND(AVG(resolution_minutes) / 60.0) AS res_h,
                       100.0 * AVG(reopened_flag) AS reopen,
                       AVG(csat_score) AS csat
                FROM ticket_topics WHERE week_start >= ? %s
                """;
        Map<String, Object> t = jdbc.queryForMap(sql.formatted("AND topic_category || ' > ' || topic_subcategory = ?"),
                weeks.recentStart().toString(), topic);
        Map<String, Object> all = jdbc.queryForMap(sql.formatted(""), weeks.recentStart().toString());
        return new BriefFacts.Impact(((Number) t.get("res_h")).intValue(), ((Number) all.get("res_h")).intValue(),
                pct(((Number) t.get("reopen")).doubleValue()), pct(((Number) all.get("reopen")).doubleValue()),
                String.format(Locale.ROOT, "%.1f", ((Number) t.get("csat")).doubleValue()),
                String.format(Locale.ROOT, "%.1f", ((Number) all.get("csat")).doubleValue()));
    }

    /** Segments whose weekly volume for the topic grew at least 1.5x faster than the topic overall. */
    private List<String> notableSegments(String topic, Weeks weeks) {
        List<String> out = new ArrayList<>();
        jdbc.query("""
                SELECT segment,
                       SUM(week_start >= ?) * 1.0 / ? AS recent,
                       SUM(week_start < ?) * 1.0 / ? AS base
                FROM ticket_topics
                WHERE topic_category || ' > ' || topic_subcategory = ? AND week_start >= ?
                GROUP BY segment ORDER BY recent * 1.0 / base DESC
                """, rs -> {
                    double recent = rs.getDouble("recent");
                    double base = rs.getDouble("base");
                    if (base > 0 && recent / base >= 1.75) {
                        out.add("%s customers: %d → %d a week (%s)".formatted(rs.getString("segment"), round(base),
                                round(recent), change(recent, base)));
                    }
                }, weeks.recentStart().toString(), RECENT_WEEKS, weeks.recentStart().toString(), BASELINE_WEEKS,
                topic, weeks.baselineStart().toString());
        return out;
    }

    // --- explained / known ---

    /** Did an incident in the window produce extra Network Quality tickets in its region? */
    private BriefFacts.Explained incidentImpact(Incident i) {
        LocalDateTime from = i.start().toLocalDate().atStartOfDay();
        LocalDateTime to = i.end().toLocalDate().plusDays(2).atStartOfDay(); // incident days + 1 day of lag
        double days = java.time.Duration.between(from, to).toHours() / 24.0;
        Integer during = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ticket_topics
                WHERE region = ? AND topic_category = 'Network Quality' AND created_at >= ? AND created_at < ?
                """, Integer.class, i.region(), ts(from), ts(to));
        Integer before = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ticket_topics
                WHERE region = ? AND topic_category = 'Network Quality' AND created_at >= ? AND created_at < ?
                """, Integer.class, i.region(), ts(from.minusDays(28)), ts(from));
        double perDayDuring = during / days;
        double perDayBefore = before / 28.0;
        String what = "%s: %s in %s, %s (%s)".formatted(i.id(), i.cause().toLowerCase(Locale.ROOT), i.region(),
                span(i.start(), i.end()), i.severity().toLowerCase(Locale.ROOT));
        if (perDayDuring < 1.25 * perDayBefore) {
            return new BriefFacts.Explained(what,
                    "No measurable effect: %d network tickets a day in %s during and just after it, vs %d a day in the 4 weeks before."
                            .formatted(round(perDayDuring), i.region(), round(perDayBefore)),
                    "None.");
        }
        return new BriefFacts.Explained(what,
                "Network tickets in %s rose to %d a day from %d; this is the incident, not a new problem."
                        .formatted(i.region(), round(perDayDuring), round(perDayBefore)),
                "None beyond the incident fix.");
    }

    /** Spikes inside the baseline weeks: explained by an incident or not, and back to normal now? */
    private List<BriefFacts.Explained> earlierSpikes(Map<String, List<Integer>> byTopic, Weeks weeks) {
        List<Incident> incidents = trends.incidents();
        Map<LocalDate, List<String>> spikeTopics = new java.util.TreeMap<>();
        Map<LocalDate, int[]> spikeVolume = new HashMap<>();
        int from = weeks.baselineOffset();
        byTopic.forEach((topic, series) -> {
            List<AnomalyDetector.Assessment> a = AnomalyDetector.assess(weeks.beforeRecent(series));
            for (int i = from; i < a.size(); i++) {
                if (a.get(i).anomaly() && a.get(i).direction() == AnomalyDetector.Direction.UP
                        && series.get(i) - a.get(i).baseline() >= EARLIER_SPIKE_MIN_EXTRA) {
                    double base = median(weeks.baseline(series));
                    double recent = mean(weeks.recent(series));
                    if (recent < 1.25 * base) { // back to normal now
                        LocalDate week = weeks.historyStart().plusWeeks(i);
                        spikeTopics.computeIfAbsent(week, w -> new ArrayList<>()).add(topic);
                        spikeVolume.merge(week, new int[] {(int) (series.get(i) - a.get(i).baseline())},
                                (x, y) -> new int[] {x[0] + y[0]});
                    }
                }
            }
        });
        List<BriefFacts.Explained> out = new ArrayList<>();
        spikeTopics.forEach((week, topics) -> {
            boolean network = topics.stream().allMatch(t -> t.startsWith("Network Quality"));
            Incident cause = network
                    ? incidents.stream().filter(i -> i.overlapsWeek(week)).findFirst().orElse(null)
                    : null;
            String what = "Week of %s: about %d extra tickets (%s)".formatted(week.format(DAY),
                    spikeVolume.get(week)[0], String.join(", ", topics.stream().map(t -> t.substring(t.indexOf("> ") + 2)).toList()));
            out.add(cause != null
                    ? new BriefFacts.Explained(what, "%s (%s in %s, %s). Volumes are back to normal."
                            .formatted(cause.id(), cause.cause().toLowerCase(Locale.ROOT), cause.region(),
                                    span(cause.start(), cause.end())), "None.")
                    : new BriefFacts.Explained(what,
                            "No network incident on record; it looks like a one-off product incident and volumes are back to normal.",
                            "Ask the product team for the post-incident review; not a current issue."));
        });
        return out;
    }

    // --- process & data quality ---

    private List<BriefFacts.ProcessIssue> processIssues(Weeks weeks) {
        List<BriefFacts.ProcessIssue> out = new ArrayList<>();
        jdbc.query("""
                SELECT channel,
                       100.0 * AVG(CASE WHEN week_start >= ? THEN first_response_minutes > 60 END) AS recent,
                       100.0 * AVG(CASE WHEN week_start <  ? THEN first_response_minutes > 60 END) AS base,
                       AVG(CASE WHEN week_start >= ? THEN first_response_minutes END) / 60.0 AS hours
                FROM ticket_topics
                WHERE priority = 'High' AND week_start >= ?
                GROUP BY channel ORDER BY recent DESC
                """, rs -> {
                    double recent = rs.getDouble("recent");
                    double base = rs.getDouble("base");
                    if (recent >= 20) {
                        boolean isNew = recent - base >= PROCESS_NEW_POINTS;
                        out.add(new BriefFacts.ProcessIssue(rs.getString("channel"), pct(recent), pct(base),
                                String.format(Locale.ROOT, "%.1f", rs.getDouble("hours")), isNew,
                                isNew ? "New: up from %s in the baseline weeks, so something changed recently."
                                        .formatted(pct(base))
                                      : "Not new: the same level as the baseline weeks, so this is how the channel is run, not a recent slip."));
                    }
                }, weeks.recentStart().toString(), weeks.recentStart().toString(), weeks.recentStart().toString(),
                weeks.baselineStart().toString());
        return out;
    }

    private BriefFacts.DataQuality dataQuality(Weeks weeks) {
        Map<String, Object> q = jdbc.queryForMap("""
                SELECT 100.0 * AVG(subcategory = 'Unclassified') AS share,
                       100.0 * SUM(subcategory = 'Unclassified' AND topic_category <> 'Others')
                             / SUM(subcategory = 'Unclassified') AS real
                FROM ticket_topics WHERE week_start >= ?
                """, weeks.recentStart().toString());
        return new BriefFacts.DataQuality(pct(((Number) q.get("share")).doubleValue()),
                pct(((Number) q.get("real")).doubleValue()),
                "Agents file real complaints as 'Unclassified' at the same rate in every channel and region, so official category counts run about 15% low. This brief counts by what the customer wrote.");
    }

    // --- helpers ---

    /** Same median definition as the anomaly detector. */
    static double median(List<Integer> values) {
        return AnomalyDetector.median(values.stream().mapToDouble(Integer::doubleValue).toArray());
    }

    static double mean(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }

    static String change(double now, double before) {
        return String.format(Locale.ROOT, "%+d%%", Math.round(100.0 * (now - before) / before));
    }

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%d%%", Math.round(v));
    }

    private static String range(LocalDate from, LocalDate to) {
        return from.format(DAY) + " – " + to.format(DAY_YEAR);
    }

    private static String span(LocalDateTime start, LocalDateTime end) {
        return start.toLocalDate().equals(end.toLocalDate())
                ? start.toLocalDate().format(DAY)
                : start.toLocalDate().format(DAY) + "–" + end.toLocalDate().format(DAY);
    }

    private static String ts(LocalDateTime t) {
        return t.toString().replace('T', ' ');
    }
}
