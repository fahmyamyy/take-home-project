package dev.cxtrends.classify;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.cxtrends.CxProperties;
import dev.cxtrends.db.Csv;

/**
 * The two label sources. The model's raw output and the human review are kept in
 * separate files so the model can be measured against the review, and so neither
 * silently overwrites the other.
 */
@Component
public class TopicFiles {

    private final TopicLabelFile llm;
    private final TopicLabelFile review;
    private final Path ambiguous;

    public TopicFiles(CxProperties props) {
        this.llm = new TopicLabelFile(props.outputDir().resolve("note_topics_llm.csv"));
        this.review = new TopicLabelFile(props.reviewDir().resolve("topic_review.csv"));
        this.ambiguous = props.reviewDir().resolve("ambiguous.csv");
    }

    /** Model output cache: output/note_topics_llm.csv. */
    public TopicLabelFile llm() {
        return llm;
    }

    /** Human-reviewed labels: review/topic_review.csv. */
    public TopicLabelFile review() {
        return review;
    }

    /**
     * Texts the reviewer marked as fitting more than one topic: review/ambiguous.csv.
     * Each candidate must be a taxonomy topic; empty if the file does not exist.
     */
    public Map<String, AmbiguousText> ambiguous() {
        Map<String, AmbiguousText> texts = new LinkedHashMap<>();
        if (!Files.exists(ambiguous)) {
            return texts;
        }
        for (Map<String, String> row : Csv.read(ambiguous)) {
            List<String> candidates = Arrays.stream(row.get("candidates").split("\\|")).map(String::strip).toList();
            for (String candidate : candidates) {
                if (Taxonomy.findByLabel(candidate) == null) {
                    throw new IllegalStateException("%s: '%s' is not a taxonomy topic".formatted(ambiguous, candidate));
                }
            }
            texts.put(row.get("base_text"), new AmbiguousText(row.get("base_text"), candidates, row.get("note")));
        }
        return texts;
    }

    public Path ambiguousPath() {
        return ambiguous;
    }
}
