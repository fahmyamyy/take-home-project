package dev.cxtrends.brief;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The brief's judgment on the real data: one genuine problem escalated, known
 * events explained rather than escalated. Uses the committed topic files.
 */
@SpringBootTest(properties = "cx.db-path=target/test-cx.db")
class BriefFactsBuilderTest {

    @Autowired
    BriefFactsBuilder builder;

    @Test
    void escalatesTheGenuineRiseAndExplainsTheRest() {
        BriefFacts facts = builder.build();

        assertThat(facts.window().recent()).isEqualTo("24 Aug – 20 Sep 2026");
        assertThat(facts.needsAttention()).singleElement().satisfies(r -> {
            assertThat(r.topic()).isEqualTo("Billing & Charging > Package charged but not active");
            assertThat(r.change()).isEqualTo("+70%");
            assertThat(r.regionsRising()).isEqualTo(6);
        });
        assertThat(facts.explained()).anySatisfy(e -> {
            assertThat(e.what()).startsWith("INC000004");
            assertThat(e.explanation()).startsWith("No measurable effect");
        });
        assertThat(facts.explained()).anySatisfy(e -> {
            assertThat(e.what()).startsWith("Week of 13 Jul");
            assertThat(e.explanation()).startsWith("INC000001");
        });
        assertThat(facts.processIssues()).anySatisfy(p -> {
            assertThat(p.channel()).isEqualTo("Email");
            assertThat(p.isNew()).isFalse();
        });
    }
}
