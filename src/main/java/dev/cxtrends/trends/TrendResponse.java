package dev.cxtrends.trends;

import java.time.LocalDate;
import java.util.List;

import dev.cxtrends.trends.AnomalyDetector.Direction;

/** Response body of {@code GET /trends}. */
public record TrendResponse(Filters filters, String anomalyRule, List<Week> weeks) {

    /**
     * Effective filters. {@code from} / {@code to} are snapped to whole weeks and
     * clipped to the data range, so they may differ from the request.
     */
    public record Filters(String category, String region, LocalDate from, LocalDate to) {
    }

    /**
     * One week of the slice.
     *
     * @param baseline       median of the previous 8 weeks; null if fewer than 4 exist
     * @param expectedLow    lower edge of the normal band
     * @param expectedHigh   upper edge of the normal band
     * @param direction      UP or DOWN when anomalous, otherwise null
     * @param knownIncidents network incidents active during this week, or that ended in
     *                       the week before (follow-up contacts lag an outage), in the
     *                       filtered region or any region when unfiltered — a possible
     *                       known explanation, not proof of one
     */
    public record Week(LocalDate weekStart, int tickets, Double baseline, Double expectedLow, Double expectedHigh,
                       boolean anomaly, Direction direction, List<IncidentRef> knownIncidents) {
    }

    /** @param relation {@code during} this week, or {@code previous_week} (ended the week before) */
    public record IncidentRef(String id, String region, String start, String end, String affectedService,
                              String cause, String severity, String relation) {

        static IncidentRef of(Incident i, String relation) {
            return new IncidentRef(i.id(), i.region(), i.start().toString(), i.end().toString(),
                    i.affectedService(), i.cause(), i.severity(), relation);
        }
    }
}
