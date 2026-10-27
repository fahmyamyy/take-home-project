package dev.cxtrends.classify;

import org.springframework.stereotype.Component;

import dev.cxtrends.CxProperties;

/**
 * The two label sources. The model's raw output and the human review are kept in
 * separate files so the model can be measured against the review, and so neither
 * silently overwrites the other.
 */
@Component
public class TopicFiles {

    private final TopicLabelFile llm;
    private final TopicLabelFile review;

    public TopicFiles(CxProperties props) {
        this.llm = new TopicLabelFile(props.outputDir().resolve("note_topics_llm.csv"));
        this.review = new TopicLabelFile(props.reviewDir().resolve("topic_review.csv"));
    }

    /** Model output cache: output/note_topics_llm.csv. */
    public TopicLabelFile llm() {
        return llm;
    }

    /** Human-reviewed labels: review/topic_review.csv. */
    public TopicLabelFile review() {
        return review;
    }
}
