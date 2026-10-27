package dev.cxtrends;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paths and external settings, bound from the {@code cx.*} keys in application.yml.
 */
@ConfigurationProperties("cx")
public record CxProperties(Path dataDir, Path outputDir, Path reviewDir, Path dbPath, Ollama ollama) {

    public record Ollama(String baseUrl, String model, Duration timeout) {
    }
}
