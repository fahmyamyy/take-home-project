package dev.cxtrends.trends;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Weekly ticket counts and the reference data needed to validate filters. */
@Repository
public class TrendRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public TrendRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DataRange(LocalDate firstWeek, LocalDate lastWeek) {
    }

    public DataRange dataRange() {
        return jdbc.getJdbcTemplate().queryForObject(
                "SELECT MIN(week_start), MAX(week_start) FROM tickets",
                (rs, n) -> new DataRange(LocalDate.parse(rs.getString(1)), LocalDate.parse(rs.getString(2))));
    }

    public List<String> categories() {
        return jdbc.getJdbcTemplate().queryForList("SELECT DISTINCT category FROM tickets ORDER BY 1", String.class);
    }

    public List<String> regions() {
        return jdbc.getJdbcTemplate().queryForList("SELECT region FROM region_base ORDER BY 1", String.class);
    }

    /**
     * Ticket counts per week (Monday) for the slice, between two Mondays inclusive.
     * Weeks without tickets are absent from the map; callers fill them with zero.
     * A null category or region means "all".
     */
    public Map<LocalDate, Integer> weeklyCounts(String category, String region, LocalDate fromWeek, LocalDate toWeek) {
        var params = new MapSqlParameterSource()
                .addValue("category", category)
                .addValue("region", region)
                .addValue("from", fromWeek.toString())
                .addValue("to", toWeek.toString());
        Map<LocalDate, Integer> counts = new HashMap<>();
        jdbc.query("""
                SELECT week_start, COUNT(*) AS tickets
                FROM tickets
                WHERE (:category IS NULL OR category = :category)
                  AND (:region IS NULL OR region = :region)
                  AND week_start BETWEEN :from AND :to
                GROUP BY week_start
                """, params, rs -> {
                    counts.put(LocalDate.parse(rs.getString("week_start")), rs.getInt("tickets"));
                });
        return counts;
    }

    public List<Incident> incidents() {
        return jdbc.getJdbcTemplate().query("""
                SELECT incident_id, region, start_time, end_time, affected_service, cause, severity
                FROM network_incidents ORDER BY start_time
                """, (rs, n) -> new Incident(rs.getString("incident_id"), rs.getString("region"),
                Incident.parseTime(rs.getString("start_time")), Incident.parseTime(rs.getString("end_time")),
                rs.getString("affected_service"), rs.getString("cause"), rs.getString("severity")));
    }
}
