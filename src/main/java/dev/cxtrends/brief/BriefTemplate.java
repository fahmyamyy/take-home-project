package dev.cxtrends.brief;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Deterministic rendering of {@link BriefFacts} as a one-page brief. Used when the
 * LLM is unavailable or its text fails the number check — and as the structure
 * the LLM is asked to follow.
 */
final class BriefTemplate {

    private BriefTemplate() {
    }

    static String render(BriefFacts f) {
        return render(f, bottomLine(f));
    }

    /** Renders the brief with the given "Bottom line" paragraph (without its label). */
    static String render(BriefFacts f, String bottomLine) {
        StringBuilder md = new StringBuilder();
        md.append("# Weekly CX Brief — ").append(f.window().recent()).append("\n\n")
                .append("_Prepaid customer tickets, last %d weeks compared with the %d weeks before (%s)._\n\n"
                        .formatted(f.window().recentWeeks(), f.window().baselineWeeks(), f.window().baseline()));

        md.append("**Bottom line:** ").append(bottomLine.strip()).append("\n\n");

        md.append("## Needs attention\n");
        int i = 1;
        for (BriefFacts.Rising r : f.needsAttention()) {
            md.append("\n### %d. %s: up %s\n\n".formatted(i++, name(r.topic()), r.change().replace("+", "")));
            if (r.customerSays() != null) {
                md.append("- **What customers say:** %s.\n".formatted(r.customerSays()));
            }
            md.append("- **Volume:** %d → %d tickets a week. Last %d weeks: %s.\n".formatted(r.baselineWeekly(),
                    r.recentWeekly(), r.weekly().size(),
                    r.weekly().stream().map(String::valueOf).collect(Collectors.joining(", "))));
            md.append("- **Why it is real:** %s\n".formatted(r.explanation()));
            if (!r.notableSegments().isEmpty()) {
                md.append("- **Growing fastest among:**\n");
                r.notableSegments().forEach(s -> md.append("  - ").append(s).append('\n'));
            }
            if (r.impact() != null) {
                BriefFacts.Impact im = r.impact();
                md.append("- **Customer impact:** these customers wait longer and come back more.\n\n")
                        .append("  | | These tickets | All tickets |\n")
                        .append("  |---|---|---|\n")
                        .append("  | Time to resolve | %d h | %d h |\n".formatted(im.resolutionHours(), im.allResolutionHours()))
                        .append("  | Reopened | %s | %s |\n".formatted(im.reopenRate(), im.allReopenRate()))
                        .append("  | Satisfaction | %s / 5 | %s / 5 |\n\n".formatted(im.csat(), im.allCsat()));
            }
            md.append("- **Ask:** assign a PIC to find the root cause and report back next week.\n");
        }
        for (BriefFacts.ProcessIssue p : f.processIssues()) {
            md.append("\n### %d. %s: %s of high-priority tickets miss the 60-minute first response\n\n"
                    .formatted(i++, p.channel(), p.missRate()));
            md.append("- **Now:** %s miss it; average first response %s hours.\n".formatted(p.missRate(),
                    p.avgFirstResponseHours()));
            md.append("- **Before:** %s in the baseline weeks.\n".formatted(p.baselineMissRate()));
            md.append("- **Read:** %s\n".formatted(p.note()));
        }

        md.append("\n## Explained — no new action needed\n\n");
        for (BriefFacts.Explained e : f.explained()) {
            md.append("- **%s.** %s %s\n".formatted(e.what(), e.explanation(),
                    e.action().equals("None.") ? "" : "_" + e.action() + "_"));
        }

        if (!f.watchList().isEmpty()) {
            md.append("\n## Watching, not escalating\n\n");
            for (BriefFacts.Rising r : f.watchList()) {
                md.append("- **%s** %s (%d → %d a week). %s\n".formatted(name(r.topic()), r.change(),
                        r.baselineWeekly(), r.recentWeekly(), r.explanation()));
            }
        }

        md.append("\n## About these numbers\n\n")
                .append("- %s of tickets were filed as \"Unclassified\", yet %s of those describe a real problem. %s\n"
                        .formatted(f.dataQuality().unclassifiedShare(), f.dataQuality().unclassifiedThatAreRealComplaints(),
                                f.dataQuality().note()));
        f.method().forEach(m -> md.append("- ").append(m).append('\n'));
        return md.toString();
    }

    /** The deterministic "Bottom line": used as-is, or replaced by the model's wording. */
    static String bottomLine(BriefFacts f) {
        if (f.needsAttention().isEmpty()) {
            return "Nothing is rising beyond normal variation, and every larger movement this period is "
                    + "explained by a known incident, so no action is needed.";
        }
        StringBuilder sb = new StringBuilder();
        for (BriefFacts.Rising r : f.needsAttention()) {
            sb.append("**%s** rose from %d to %d tickets a week (%s) in %d of %d regions, and no outage explains it%s. "
                    .formatted(name(r.topic()), r.baselineWeekly(), r.recentWeekly(), r.change(), r.regionsRising(),
                            r.regionsTotal(), r.customerSays() == null ? "" : ": " + r.customerSays()));
        }
        sb.append("Assign a PIC to find the root cause and report back next week. ")
                .append("Everything else that moved is explained by a known incident or within normal range.");
        return sb.toString();
    }

    /** "Billing & Charging > Package charged but not active" → "Package charged but not active". */
    static String name(String topic) {
        int i = topic.indexOf("> ");
        return i < 0 ? topic : topic.substring(i + 2);
    }

    /** Phrases the model's "Bottom line" must contain: every topic that needs attention. */
    static List<String> requiredPhrases(BriefFacts f) {
        return f.needsAttention().stream().map(r -> name(r.topic())).toList();
    }
}
