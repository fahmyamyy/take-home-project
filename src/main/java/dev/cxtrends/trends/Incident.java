package dev.cxtrends.trends;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** A known outage or maintenance window from network_incidents.csv. */
public record Incident(String id, String region, LocalDateTime start, LocalDateTime end,
                       String affectedService, String cause, String severity) {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static LocalDateTime parseTime(String value) {
        return LocalDateTime.parse(value, FORMAT);
    }

    /** True if the incident was active at any time during the week starting on {@code monday}. */
    public boolean overlapsWeek(LocalDate monday) {
        return overlaps(monday.atStartOfDay(), monday.plusWeeks(1).atStartOfDay());
    }

    /**
     * True if the incident ended during the week before {@code monday} (and was not
     * active in this one). Customers keep calling back after an outage: after the July
     * fiber cut, three quarters of the next week's network tickets in that region were
     * follow-ups.
     */
    public boolean endedWeekBefore(LocalDate monday) {
        return !overlapsWeek(monday) && overlaps(monday.minusWeeks(1).atStartOfDay(), monday.atStartOfDay());
    }

    private boolean overlaps(LocalDateTime from, LocalDateTime to) {
        return start.isBefore(to) && end.isAfter(from);
    }
}
