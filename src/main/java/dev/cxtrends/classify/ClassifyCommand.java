package dev.cxtrends.classify;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import dev.cxtrends.db.DataLoader;

/**
 * Part 3a. Classifies every distinct complaint text that is not yet in the cache
 * (so Ollama is only needed the first time), then writes the comparison report
 * against the agent labels.
 */
@Component
public class ClassifyCommand {

    private static final Logger log = LoggerFactory.getLogger(ClassifyCommand.class);

    private final JdbcTemplate jdbc;
    private final TopicFiles files;
    private final TopicClassifier classifier;
    private final OllamaClient ollama;
    private final DataLoader loader;
    private final ClassificationReport report;

    public ClassifyCommand(JdbcTemplate jdbc, TopicFiles files, TopicClassifier classifier, OllamaClient ollama,
                           DataLoader loader, ClassificationReport report) {
        this.jdbc = jdbc;
        this.files = files;
        this.classifier = classifier;
        this.ollama = ollama;
        this.loader = loader;
        this.report = report;
    }

    public void run() {
        Map<String, TopicLabel> topics = new LinkedHashMap<>(files.llm().load());
        List<String> pending = jdbc.queryForList(
                "SELECT DISTINCT base_text FROM ticket_notes ORDER BY base_text", String.class).stream()
                .filter(text -> !topics.containsKey(text))
                .toList();

        if (pending.isEmpty()) {
            log.info("all {} complaint texts already classified in {}; Ollama not needed", topics.size(), files.llm().path());
        } else {
            if (!ollama.available()) {
                throw new IllegalStateException(("%d texts need classifying but Ollama is not reachable or model "
                        + "'%s' is not pulled. Run: ollama pull %s").formatted(pending.size(), ollama.model(),
                        ollama.model()));
            }
            log.info("classifying {} new complaint texts with {}", pending.size(), ollama.model());
            for (String text : pending) {
                TopicLabel topic = classifier.classify(text);
                topics.put(text, topic);
                files.llm().save(topics.values()); // save as we go, so an interrupted run resumes
                log.info("  {} -> {} > {}", text, topic.category(), topic.subcategory());
            }
        }

        loader.reloadNoteTopics();
        report.write();
    }
}
