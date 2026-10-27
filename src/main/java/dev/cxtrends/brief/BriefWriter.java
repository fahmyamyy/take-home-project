package dev.cxtrends.brief;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.cxtrends.classify.OllamaClient;

/**
 * Turns {@link BriefFacts} into the executive brief.
 *
 * <p>The body is the deterministic {@link BriefTemplate}. The local model writes
 * only the opening "Bottom line" (≤ {@value #MAX_WORDS} words), and that text is used
 * only if it passes the {@link NumberGuard}, names every topic that needs attention
 * and stays short. Letting the 3B model rewrite the whole brief produced text whose
 * numbers were all correct but whose meaning drifted (docs/experiments/full-llm-brief.md);
 * a short paragraph is where fluent wording matters most and is still checkable.
 */
@Component
public class BriefWriter {

    private static final Logger log = LoggerFactory.getLogger(BriefWriter.class);
    private static final int MAX_ATTEMPTS = 3;
    static final int MAX_WORDS = 70;
    /** The ask every bottom line must carry, word for word (PIC = person in charge). */
    static final String ASK = "Assign a PIC to find the root cause and report back next week.";
    /** Phrases that sound like action but ask nothing specific. */
    static final List<String> FILLER = List.of("please", "promptly", "address this issue", "in recent weeks");

    static final String SYSTEM_PROMPT = """
            You write the opening "bottom line" of a weekly customer-service brief for a
            busy, non-technical executive at a prepaid mobile operator. You get FACTS (JSON).
            The executive may read nothing else, so say what to act on, not how things went.

            Write exactly 3 plain sentences, at most %d words in total:
            1. The problem under needsAttention, described as what the customer experiences
               (use customerSays), with its weekly numbers before and now and its change,
               and why it is real: how many regions it rose in, and that no outage explains it.
            2. The ask, in exactly these words: "Assign a PIC to find the root cause and report back next week."
            3. Everything else that moved (under explained and watchList) is already
               explained or within normal range, so it needs no action.

            Shape (fill in from the FACTS):
            "<What customers experience>: <topic> rose from <before> to <now> tickets a week
            (<change>) in <n> of <total> regions, and no outage explains it. Assign a PIC to
            find the root cause and report back next week. The other spikes this period are
            explained by known incidents or within normal range, so they need no action."

            Rules:
            - Use only numbers that appear in the FACTS, written exactly as there. Do not
              calculate, round or invent any number, date or percentage.
            - Name each needsAttention topic by the words after "> " only, for example
              "Package charged but not active", never with its category or the ">" sign.
            - Weekly numbers count tickets, not customers: write "tickets a week".
            - Do not add causes or teams that are not in the FACTS.
            - No filler: never write "please", "promptly", "address this issue" or "in recent weeks".
            - Reply with the sentences only: no heading, no label, no list, no quotes.
            """.formatted(MAX_WORDS);

    private final OllamaClient ollama;

    public BriefWriter(OllamaClient ollama) {
        this.ollama = ollama;
    }

    public record Result(String markdown, String generator) {
    }

    public Result write(BriefFacts facts, String factsJson) {
        String fallback = BriefTemplate.render(facts);
        if (!ollama.available()) {
            log.warn("Ollama/{} not available; using the template bottom line", ollama.model());
            return new Result(fallback, "template only (Ollama was not available)");
        }
        NumberGuard guard = new NumberGuard(factsJson);
        List<String> required = BriefTemplate.requiredPhrases(facts);
        String feedback = "";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String text = ollama.chat(SYSTEM_PROMPT, "FACTS:\n" + forModel(factsJson) + feedback, null)
                    .strip().replaceFirst("(?i)^\\**bottom line:?\\**:?\\s*", "");
            String problem = check(text, guard, required);
            if (problem == null) {
                return new Result(BriefTemplate.render(facts, text),
                        "bottom line by %s (attempt %d, numbers checked against brief_facts.json); body from template"
                                .formatted(ollama.model(), attempt));
            }
            log.info("attempt {} rejected: {}", attempt, problem);
            feedback = "\n\nYour previous answer was rejected: " + problem + ". Try again.";
        }
        log.warn("model bottom line failed the checks {} times; using the template", MAX_ATTEMPTS);
        return new Result(fallback, "template only (%s failed the checks %d times)".formatted(ollama.model(),
                MAX_ATTEMPTS));
    }

    /**
     * The facts as the model sees them: topics without their category prefix, so the
     * model copies "Package charged but not active" rather than "Billing &amp; Charging &gt; …".
     */
    static String forModel(String factsJson) {
        return factsJson.replaceAll("(\"topic\"\\s*:\\s*\")[^\">]*> ", "$1");
    }

    /** Null if the text is usable, otherwise why not. */
    static String check(String text, NumberGuard guard, List<String> required) {
        List<String> unknown = guard.unknownNumbers(text);
        if (!unknown.isEmpty()) {
            return "it used numbers not in the FACTS: " + unknown;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> missing = required.stream().filter(p -> !lower.contains(p.toLowerCase(Locale.ROOT))).toList();
        if (!missing.isEmpty()) {
            return "it did not name: " + missing;
        }
        if (!lower.contains(ASK.toLowerCase(Locale.ROOT))) {
            return "it must contain the ask exactly: \"" + ASK + "\"";
        }
        if (text.contains(">")) {
            return "it named a topic with its category; use only the words after \"> \"";
        }
        List<String> filler = FILLER.stream().filter(lower::contains).toList();
        if (!filler.isEmpty()) {
            return "it used filler phrases: " + filler;
        }
        int words = text.split("\\s+").length;
        if (words > MAX_WORDS) {
            return "it has %d words; the limit is %d".formatted(words, MAX_WORDS);
        }
        if (text.contains("\n#") || text.startsWith("#") || text.contains("\n- ") || text.contains("\n1.")) {
            return "it must be plain sentences, not headings or lists";
        }
        return null;
    }
}
