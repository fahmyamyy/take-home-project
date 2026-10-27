package dev.cxtrends.classify;

import java.util.List;

/**
 * A complaint text that fits more than one topic. The human review picks one of the
 * candidates; the pipeline keeps the text flagged so it is reported separately and
 * always goes to the human-check queue.
 *
 * @param candidates every defensible topic, as "category > subcategory"
 * @param note       why the text is ambiguous and which way the review decided
 */
public record AmbiguousText(String baseText, List<String> candidates, String note) {
}
