package dev.cxtrends.trends;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Flags weeks whose volume departs from the recent trend.
 *
 * <p>Rule: compare each week with the <b>median of the previous 8 weeks</b>; flag it
 * when the difference exceeds {@code max(3 × MAD × 1.4826, 25% of the median, 5 tickets)}.
 *
 * <ul>
 *   <li><b>Median / MAD instead of mean / standard deviation:</b> one outage week
 *       (e.g. the July fiber cut, 20x normal volume in one region) would inflate a
 *       standard deviation for the next 8 weeks and hide any new problem. The median
 *       and MAD ignore a single outlier.</li>
 *   <li><b>1.4826 × MAD</b> estimates σ for normally distributed counts, so "3" reads
 *       like the familiar 3-sigma rule.</li>
 *   <li><b>Floors (25%, 5 tickets):</b> on small slices (one category in one region)
 *       the MAD can be 0–2 tickets and every wobble would be flagged. A change also
 *       has to be big in relative and absolute terms to matter to anyone.</li>
 *   <li><b>At least 4 weeks of history</b> before anything is flagged; earlier weeks
 *       get no baseline rather than a guess.</li>
 * </ul>
 */
public final class AnomalyDetector {

    public static final int WINDOW = 8;
    public static final int MIN_HISTORY = 4;
    static final double K = 3.0;
    static final double MAD_TO_SIGMA = 1.4826;
    static final double MIN_RELATIVE = 0.25;
    static final double MIN_ABSOLUTE = 5.0;

    public enum Direction { UP, DOWN }

    /**
     * Assessment of one week. {@code baseline}, {@code lower} and {@code upper} are null
     * when there was not enough history; {@code direction} is null unless anomalous.
     */
    public record Assessment(Double baseline, Double lower, Double upper, boolean anomaly, Direction direction) {

        static final Assessment INSUFFICIENT_HISTORY = new Assessment(null, null, null, false, null);
    }

    private AnomalyDetector() {
    }

    /** Assesses every week of {@code counts} against the weeks before it. */
    public static List<Assessment> assess(List<Integer> counts) {
        List<Assessment> result = new ArrayList<>(counts.size());
        for (int i = 0; i < counts.size(); i++) {
            List<Integer> history = counts.subList(Math.max(0, i - WINDOW), i);
            result.add(assessWeek(counts.get(i), history));
        }
        return result;
    }

    static Assessment assessWeek(int count, List<Integer> history) {
        if (history.size() < MIN_HISTORY) {
            return Assessment.INSUFFICIENT_HISTORY;
        }
        double[] values = history.stream().mapToDouble(Integer::doubleValue).toArray();
        double median = median(values);
        double[] deviations = Arrays.stream(values).map(v -> Math.abs(v - median)).toArray();
        double sigma = MAD_TO_SIGMA * median(deviations);
        double threshold = Math.max(K * sigma, Math.max(MIN_RELATIVE * median, MIN_ABSOLUTE));

        double lower = Math.max(0, median - threshold);
        double upper = median + threshold;
        Direction direction = count > upper ? Direction.UP : count < lower ? Direction.DOWN : null;
        return new Assessment(median, lower, upper, direction != null, direction);
    }

    public static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2.0;
    }
}
