package dev.cxtrends.brief;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class BriefWriterTest {

    private static final String FACTS = """
            {"volume": {"recentWeeklyAvg": 1665, "change": "+2%"},
             "needsAttention": [{"topic": "Billing & Charging > Package charged but not active",
                                 "baselineWeekly": 109, "recentWeekly": 186, "change": "+70%"}],
             "impact": {"csat": "3.7"}}
            """;
    private final NumberGuard guard = new NumberGuard(FACTS);
    private final List<String> required = List.of("Package charged but not active");

    @Test
    void acceptsTextThatOnlyUsesFactNumbers() {
        String text = "Volume is flat at 1,665 a week (+2%). Package charged but not active is up 70%, "
                + "from 109 to 186 a week. Assign a PIC to find the root cause and report back next week.";

        assertThat(BriefWriter.check(text, guard, required)).isNull();
    }

    @Test
    void rejectsInventedNumbers() {
        String text = "Package charged but not active rose to 190 a week, about 2,000 customers.";

        assertThat(BriefWriter.check(text, guard, required)).contains("[190, 2000]");
    }

    @Test
    void rejectsRoundedNumbers() {
        assertThat(BriefWriter.check("Package charged but not active is up 1.7x.", guard, required))
                .contains("1.7");
    }

    @Test
    void rejectsTextThatDropsAFlaggedTopic() {
        assertThat(BriefWriter.check("Volume is flat at 1,665 a week.", guard, required))
                .contains("did not name");
    }

    @Test
    void rejectsLongOrStructuredText() {
        String longText = "Package charged but not active. " + BriefWriter.ASK + " word".repeat(BriefWriter.MAX_WORDS);
        assertThat(BriefWriter.check(longText, guard, required)).contains("words");
        assertThat(BriefWriter.check("Package charged but not active. " + BriefWriter.ASK + "\n- item", guard, required))
                .contains("lists");
    }

    @Test
    void rejectsTopicWithItsCategory() {
        String text = "Billing & Charging > Package charged but not active is up 70%. " + BriefWriter.ASK;
        assertThat(BriefWriter.check(text, guard, required)).contains("category");
    }

    @Test
    void rejectsFillerThatAsksNothingSpecific() {
        String text = "Package charged but not active is up 70%. Please address this issue promptly. " + BriefWriter.ASK;
        assertThat(BriefWriter.check(text, guard, required)).contains("filler");
    }

    @Test
    void modelSeesTopicsWithoutTheirCategory() {
        assertThat(BriefWriter.forModel(FACTS))
                .contains("\"topic\": \"Package charged but not active\"")
                .doesNotContain(">");
    }

    @Test
    void rejectsTextWithoutTheAsk() {
        String text = "Package charged but not active is up 70%. Name an owner to look into it.";
        assertThat(BriefWriter.check(text, guard, required)).contains("Assign a PIC");
    }

    @Test
    void listMarkersAreNotFigures() {
        assertThat(guard.unknownNumbers("1. Package charged but not active\n2) next")).isEmpty();
    }
}
