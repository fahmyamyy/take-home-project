package dev.cxtrends.db;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import dev.cxtrends.CxProperties;
import dev.cxtrends.classify.NoteNormalizer;
import dev.cxtrends.classify.TopicFiles;
import dev.cxtrends.classify.TopicLabel;

/**
 * Rebuilds cx.db from the CSVs in data/ (and the classification cache in output/)
 * on every start. 42k rows load in about a second, and rebuilding means the
 * database can never be stale relative to its sources.
 */
@Component
public class DataLoader implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final CxProperties props;
    private final TopicFiles topicFiles;

    public DataLoader(JdbcTemplate jdbc, TransactionTemplate tx, CxProperties props, TopicFiles topicFiles) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
        this.topicFiles = topicFiles;
    }

    @Override
    public void afterPropertiesSet() {
        load();
    }

    public void load() {
        long started = System.nanoTime();
        tx.executeWithoutResult(status -> {
            for (SqlScript.Statement s : SqlScript.read("/sql/schema.sql")) {
                jdbc.execute(s.sql());
            }
            loadTickets();
            loadNotes();
            loadIncidents();
            loadRegions();
            reloadNoteTopics();
        });
        log.info("cx.db loaded from {} in {} ms", props.dataDir(), (System.nanoTime() - started) / 1_000_000);
    }

    /**
     * Rebuilds note_topics from the label files: the reviewed label wins, the model's
     * label fills in for texts nobody has reviewed yet.
     */
    public void reloadNoteTopics() {
        jdbc.update("DELETE FROM note_topics");
        Map<String, TopicLabel> llm = topicFiles.llm().load();
        Map<String, TopicLabel> review = topicFiles.review().load();
        java.util.Set<String> texts = new java.util.TreeSet<>(llm.keySet());
        texts.addAll(review.keySet());
        List<Object[]> rows = new ArrayList<>();
        for (String text : texts) {
            TopicLabel model = llm.get(text);
            TopicLabel reviewed = review.get(text);
            TopicLabel chosen = reviewed != null ? reviewed : model;
            rows.add(new Object[] {text, chosen.category(), chosen.subcategory(), reviewed != null ? "review" : "llm",
                    model == null ? null : model.category(), model == null ? null : model.subcategory()});
        }
        jdbc.batchUpdate("INSERT INTO note_topics VALUES (?, ?, ?, ?, ?, ?)", rows);
    }

    private void loadTickets() {
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, String> r : csv("tickets.csv")) {
            String createdAt = r.get("created_at");
            rows.add(new Object[] {
                    r.get("ticket_id"), r.get("subscriber_id"), createdAt, weekStart(createdAt),
                    r.get("channel"), r.get("category"), r.get("subcategory"), r.get("segment"),
                    r.get("region"), r.get("priority"), r.get("status"),
                    Integer.parseInt(r.get("first_response_minutes")),
                    nullableInt(r.get("resolution_minutes")),
                    Integer.parseInt(r.get("reopened_flag")),
                    nullableInt(r.get("csat_score"))});
        }
        jdbc.batchUpdate("INSERT INTO tickets VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private void loadNotes() {
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, String> r : csv("ticket_notes.csv")) {
            NoteNormalizer.Normalized n = NoteNormalizer.normalize(r.get("note_text"));
            rows.add(new Object[] {r.get("ticket_id"), r.get("note_text"), n.baseText(), n.followUpDepth()});
        }
        jdbc.batchUpdate("INSERT INTO ticket_notes VALUES (?, ?, ?, ?)", rows);
    }

    private void loadIncidents() {
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, String> r : csv("network_incidents.csv")) {
            rows.add(new Object[] {r.get("incident_id"), r.get("region"), r.get("start_time"), r.get("end_time"),
                    r.get("affected_service"), r.get("cause"), r.get("severity")});
        }
        jdbc.batchUpdate("INSERT INTO network_incidents VALUES (?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private void loadRegions() {
        List<Object[]> rows = new ArrayList<>();
        for (Map<String, String> r : csv("region_base.csv")) {
            rows.add(new Object[] {r.get("region"), Long.parseLong(r.get("subscriber_base"))});
        }
        jdbc.batchUpdate("INSERT INTO region_base VALUES (?, ?)", rows);
    }

    private List<Map<String, String>> csv(String name) {
        Path file = props.dataDir().resolve(name);
        if (!Files.exists(file)) {
            throw new UncheckedIOException(new IOException("missing data file " + file.toAbsolutePath()));
        }
        return Csv.read(file);
    }

    /** Monday of the week containing the timestamp ('YYYY-MM-DD HH:MM:SS'). */
    static String weekStart(String timestamp) {
        return LocalDate.parse(timestamp.substring(0, 10))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toString();
    }

    private static Integer nullableInt(String value) {
        return value == null || value.isBlank() ? null : Integer.valueOf(value.strip());
    }
}
