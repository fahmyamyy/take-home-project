package dev.cxtrends.classify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.cxtrends.CxProperties;

class TopicFilesTest {

    @TempDir
    Path review;

    @Test
    void readsAmbiguousTextsWithTheirCandidates() throws Exception {
        Files.writeString(review.resolve("ambiguous.csv"), """
                base_text,candidates,note
                paket tidak aktif,Package & Quota > Package not active after purchase | Billing & Charging > Package charged but not active,"either fits"
                """);

        AmbiguousText text = files().ambiguous().get("paket tidak aktif");

        assertThat(text.candidates()).containsExactly(
                "Package & Quota > Package not active after purchase",
                "Billing & Charging > Package charged but not active");
        assertThat(text.note()).isEqualTo("either fits");
    }

    @Test
    void rejectsACandidateThatIsNotATopic() throws Exception {
        Files.writeString(review.resolve("ambiguous.csv"), """
                base_text,candidates,note
                something,Others > Unclassified | Others > Made up,x
                """);

        assertThatThrownBy(() -> files().ambiguous()).hasMessageContaining("'Others > Made up' is not a taxonomy topic");
    }

    @Test
    void noFileMeansNothingIsAmbiguous() {
        assertThat(files().ambiguous()).isEmpty();
    }

    private TopicFiles files() {
        return new TopicFiles(new CxProperties(review, review, review, review.resolve("cx.db"),
                new CxProperties.Ollama("http://localhost:11434", "qwen2.5:7b", "qwen2.5:3b", Duration.ofSeconds(1))));
    }
}
