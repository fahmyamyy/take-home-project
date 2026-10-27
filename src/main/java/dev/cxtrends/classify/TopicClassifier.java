package dev.cxtrends.classify;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Assigns one taxonomy topic to a complaint text with the local LLM.
 *
 * <p>The reply is constrained by a JSON schema whose {@code topic} field is an enum of
 * the valid "Category > Subcategory" labels, so Ollama's constrained decoding cannot
 * produce a label outside the taxonomy. (An earlier version asked for category and
 * subcategory separately; the 3B model then mixed them, e.g. "Billing & Charging >
 * Auto-renew unwanted".) The label is still validated, and retried if invalid.
 *
 * <p>The model must restate the problem <i>before</i> choosing the topic. Generating
 * the label first and a reason after it scored 71/91 against the human review;
 * restating first scored 81/91 with the same model, prompt and taxonomy.
 */
@Component
public class TopicClassifier {

    private static final int MAX_ATTEMPTS = 3;

    static final String SYSTEM_PROMPT = """
            You label customer complaint notes for a prepaid mobile operator in Indonesia.
            Notes are written in Indonesian, English, or a mix of both.
            Pick exactly one topic from the list below — the one that best describes the
            customer's actual problem. Judge by what the customer describes, not by
            keywords alone. Use "Others > Unclassified" only if the note says nothing
            about the problem.

            Topics (category > subcategory: definition):
            %s

            First restate the customer's problem in plain English in "problem" (<= 15 words),
            then choose "topic".
            Reply with JSON: {"problem": "...", "topic": "<category > subcategory, exactly as listed>"}.
            """.formatted(Taxonomy.TOPICS.stream()
            .map(t -> "- " + t.label() + ": " + t.definition())
            .collect(Collectors.joining("\n")));

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of(
                    "problem", Map.of("type", "string"),
                    "topic", Map.of("type", "string", "enum", Taxonomy.TOPICS.stream().map(Taxonomy.Topic::label).toList())),
            "required", List.of("problem", "topic"));

    private final OllamaClient ollama;
    private final ObjectMapper json;

    public TopicClassifier(OllamaClient ollama, ObjectMapper json) {
        this.ollama = ollama;
        this.json = json;
    }

    public TopicLabel classify(String baseText) {
        String user = "Note: " + baseText;
        String lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String prompt = lastError == null ? user
                    : user + "\n\nYour previous answer was invalid: " + lastError + ". Use a topic exactly as listed.";
            JsonNode reply = json.readTree(ollama.chat(SYSTEM_PROMPT, prompt, SCHEMA));
            String label = reply.path("topic").asString();
            Taxonomy.Topic topic = Taxonomy.findByLabel(label);
            if (topic != null) {
                return new TopicLabel(baseText, topic.category(), topic.subcategory(), reply.path("problem").asString());
            }
            lastError = "'%s' is not in the list".formatted(label);
        }
        throw new IllegalStateException("no valid topic for '%s' after %d attempts (%s)"
                .formatted(baseText, MAX_ATTEMPTS, lastError));
    }
}
