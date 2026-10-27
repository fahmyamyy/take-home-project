package dev.cxtrends;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paths and external settings, bound from the {@code cx.*} keys in application.yml.
 */
@ConfigurationProperties("cx")
public record CxProperties(Path dataDir, Path outputDir, Path reviewDir, Path dbPath, Ollama ollama) {

    /**
     * @param classifyModel model for note classification (Part 3a)
     * @param briefModel    model for the brief's bottom line (Part 3b)
     */
    public record Ollama(String baseUrl, String classifyModel, String briefModel, Duration timeout) {
    }
}
