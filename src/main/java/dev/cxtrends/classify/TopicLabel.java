package dev.cxtrends.classify;

/**
 * A topic assigned to one distinct complaint text, by the model or by a reviewer.
 *
 * @param note the model's restatement of the problem, or the reviewer's comment
 */
public record TopicLabel(String baseText, String category, String subcategory, String note) {
}
