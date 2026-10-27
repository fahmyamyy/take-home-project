package dev.cxtrends.classify;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.cxtrends.db.Csv;

/**
 * A CSV of {@link TopicLabel}s keyed by base text. Used for both the model's cache
 * (output/note_topics_llm.csv) and the human review (review/topic_review.csv).
 */
public final class TopicLabelFile {

    private static final String HEADER = "base_text,category,subcategory,note";

    private final Path file;

    public TopicLabelFile(Path file) {
        this.file = file;
    }

    public Path path() {
        return file;
    }

    /** Labels keyed by base text; empty if the file does not exist yet. */
    public Map<String, TopicLabel> load() {
        Map<String, TopicLabel> labels = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return labels;
        }
        for (Map<String, String> row : Csv.read(file)) {
            TopicLabel label = new TopicLabel(row.get("base_text"), row.get("category"), row.get("subcategory"),
                    row.get("note"));
            if (Taxonomy.findByLabel(label.category() + " > " + label.subcategory()) == null) {
                throw new IllegalStateException("%s: '%s > %s' is not a taxonomy topic"
                        .formatted(file, label.category(), label.subcategory()));
            }
            labels.put(label.baseText(), label);
        }
        return labels;
    }

    public void save(Collection<TopicLabel> labels) {
        List<TopicLabel> sorted = new ArrayList<>(labels);
        sorted.sort(Comparator.comparing(TopicLabel::category)
                .thenComparing(TopicLabel::subcategory)
                .thenComparing(TopicLabel::baseText));
        List<String> lines = new ArrayList<>(sorted.size() + 1);
        lines.add(HEADER);
        for (TopicLabel l : sorted) {
            lines.add(String.join(",", Csv.escape(l.baseText()), Csv.escape(l.category()),
                    Csv.escape(l.subcategory()), Csv.escape(l.note())));
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, lines);
        } catch (IOException e) {
            throw new UncheckedIOException("write " + file, e);
        }
    }
}
