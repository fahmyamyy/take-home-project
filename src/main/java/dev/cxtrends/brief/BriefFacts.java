package dev.cxtrends.brief;

import java.util.List;

/**
 * Everything the weekly brief is allowed to say, computed by code. The LLM only
 * turns this into prose; the number guard rejects any figure not found here.
 *
 * <p>Topics are our note-based topics (Part 3a), not the agent label, because the
 * agent label misses ~15% of every real topic.
 */
public record BriefFacts(
        Window window,
        Volume volume,
        List<Rising> needsAttention,
        List<Rising> watchList,
        List<Explained> explained,
        List<ProcessIssue> processIssues,
        DataQuality dataQuality,
        List<String> method) {

    /** The last 4 complete weeks compared with the 8 weeks before them. */
    public record Window(String recent, String baseline, int recentWeeks, int baselineWeeks) {
    }

    public record Volume(int recentWeeklyAvg, int baselineWeeklyMedian, String change) {
    }

    /**
     * A topic whose recent weekly volume is above its baseline.
     *
     * @param customerSays  what the topic means, from the taxonomy definition
     * @param weekly        the 4 recent weekly counts, oldest first
     * @param regionsRising how many of the regions are individually up ≥ 20%
     * @param explanation   why it is (or is not) attributed to a known cause
     */
    public record Rising(String topic, String customerSays, int baselineWeekly, int recentWeekly, String change, List<Integer> weekly,
                         int regionsRising, int regionsTotal, String explanation, Impact impact,
                         List<String> notableSegments) {
    }

    /** How the topic's tickets fare compared with all tickets in the same 4 weeks. */
    public record Impact(int resolutionHours, int allResolutionHours, String reopenRate, String allReopenRate,
                         String csat, String allCsat) {
    }

    /** Something that moved (or might have) and already has a known explanation. */
    public record Explained(String what, String explanation, String action) {
    }

    /**
     * @param missRate         share of high-priority tickets answered after 60 minutes, last 4 weeks
     * @param baselineMissRate the same share in the baseline weeks
     * @param isNew            false when the rate is at its baseline level (a chronic issue)
     */
    public record ProcessIssue(String channel, String missRate, String baselineMissRate,
                               String avgFirstResponseHours, boolean isNew, String note) {
    }

    public record DataQuality(String unclassifiedShare, String unclassifiedThatAreRealComplaints, String note) {
    }
}
