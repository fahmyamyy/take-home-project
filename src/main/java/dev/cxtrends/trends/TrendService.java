package dev.cxtrends.trends;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import dev.cxtrends.trends.AnomalyDetector.Assessment;

/**
 * Builds the weekly series for a slice and flags anomalies.
 *
 * <p>Weeks are Monday–Sunday. {@code from} snaps back to its Monday and {@code to}
 * forward to its Sunday, so every returned week is complete and counts are
 * comparable. The 8 weeks <i>before</i> {@code from} are read too (when the data
 * has them), so the first weeks of a narrow date range still get a baseline.
 */
@Service
public class TrendService {

    static final String RULE = "A week is anomalous when its count is outside median(previous 8 weeks) "
            + "± max(3 × 1.4826 × MAD, 25% of median, 5 tickets). Needs ≥ 4 weeks of history.";

    private final TrendRepository repository;

    public TrendService(TrendRepository repository) {
        this.repository = repository;
    }

    public record TrendQuery(String category, String region, LocalDate from, LocalDate to) {
    }

    public TrendResponse trends(TrendQuery query) {
        String category = validated("category", query.category(), repository.categories());
        String region = validated("region", query.region(), repository.regions());
        if (query.from() != null && query.to() != null && query.from().isAfter(query.to())) {
            throw new IllegalArgumentException("'from' (%s) is after 'to' (%s)".formatted(query.from(), query.to()));
        }

        TrendRepository.DataRange range = repository.dataRange();
        LocalDate fromWeek = latest(monday(query.from() != null ? query.from() : range.firstWeek()), range.firstWeek());
        LocalDate toWeek = earliest(monday(query.to() != null ? query.to() : range.lastWeek()), range.lastWeek());
        if (fromWeek.isAfter(toWeek)) {
            return new TrendResponse(new TrendResponse.Filters(category, region, null, null), RULE, List.of());
        }
        LocalDate historyStart = latest(fromWeek.minusWeeks(AnomalyDetector.WINDOW), range.firstWeek());

        Map<LocalDate, Integer> counts = repository.weeklyCounts(category, region, historyStart, toWeek);
        List<LocalDate> weeks = new ArrayList<>();
        List<Integer> series = new ArrayList<>();
        for (LocalDate w = historyStart; !w.isAfter(toWeek); w = w.plusWeeks(1)) {
            weeks.add(w);
            series.add(counts.getOrDefault(w, 0));
        }
        List<Assessment> assessments = AnomalyDetector.assess(series);
        List<Incident> incidents = repository.incidents().stream()
                .filter(i -> region == null || i.region().equals(region))
                .toList();

        List<TrendResponse.Week> result = new ArrayList<>();
        for (int i = 0; i < weeks.size(); i++) {
            LocalDate week = weeks.get(i);
            if (week.isBefore(fromWeek)) {
                continue; // history only
            }
            Assessment a = assessments.get(i);
            List<TrendResponse.IncidentRef> known = new ArrayList<>();
            for (Incident inc : incidents) {
                if (inc.overlapsWeek(week)) {
                    known.add(TrendResponse.IncidentRef.of(inc, "during"));
                } else if (inc.endedWeekBefore(week)) {
                    known.add(TrendResponse.IncidentRef.of(inc, "previous_week"));
                }
            }
            result.add(new TrendResponse.Week(week, series.get(i), round(a.baseline()), round(a.lower()),
                    round(a.upper()), a.anomaly(), a.direction(), known));
        }
        return new TrendResponse(
                new TrendResponse.Filters(category, region, fromWeek, toWeek.plusDays(6)), RULE, result);
    }

    /** Returns the canonical spelling of {@code value} (case-insensitive match), or null for "all". */
    private static String validated(String name, String value, List<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return allowed.stream()
                .filter(a -> a.equalsIgnoreCase(value.strip()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown %s '%s'; expected one of %s".formatted(name, value, allowed)));
    }

    private static LocalDate monday(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private static LocalDate latest(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 10.0) / 10.0;
    }
}
