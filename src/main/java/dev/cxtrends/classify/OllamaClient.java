package dev.cxtrends.classify;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.cxtrends.CxProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Calls a local Ollama server's {@code /api/chat}. Plain JDK HttpClient: one
 * endpoint does not justify an LLM framework.
 *
 * <p>Temperature 0 and a fixed seed make output as repeatable as the model allows;
 * results are cached by the callers anyway.
 */
@Component
public class OllamaClient {

    private final CxProperties.Ollama config;
    private final ObjectMapper json;
    private final HttpClient http;

    public OllamaClient(CxProperties props, ObjectMapper json) {
        this.config = props.ollama();
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(config.timeout()).build();
    }

    /** True if the server answers and has {@code model} pulled. */
    public boolean available(String model) {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(config.baseUrl() + "/api/tags")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }
            for (JsonNode m : json.readTree(response.body()).path("models")) {
                if (m.path("name").asString().equals(model)) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Sends a system + user prompt and returns the assistant's reply.
     *
     * @param format null for free text, or a JSON schema the reply must follow
     */
    public String chat(String model, String system, String user, Map<String, Object> format) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        body.put("options", Map.of("temperature", 0, "seed", 42));
        if (format != null) {
            body.put("format", format);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + "/api/chat"))
                .timeout(config.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("ollama returned %d: %s".formatted(response.statusCode(), response.body()));
            }
            return json.readTree(response.body()).path("message").path("content").asString();
        } catch (IOException e) {
            throw new IllegalStateException("ollama request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for ollama", e);
        }
    }
}
