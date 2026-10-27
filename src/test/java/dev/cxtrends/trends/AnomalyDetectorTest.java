package dev.cxtrends.trends;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import dev.cxtrends.trends.AnomalyDetector.Assessment;
import dev.cxtrends.trends.AnomalyDetector.Direction;

class AnomalyDetectorTest {

    static Stream<Arguments> lastWeek() {
        return Stream.of(
                Arguments.of("flat series", List.of(100, 102, 98, 101, 99, 100, 103, 97, 100), null),
                Arguments.of("spike", List.of(100, 102, 98, 101, 99, 100, 103, 97, 180), Direction.UP),
                Arguments.of("drop", List.of(100, 102, 98, 101, 99, 100, 103, 97, 40), Direction.DOWN),
                Arguments.of("normal wobble on a noisy series",
                        List.of(80, 120, 90, 115, 85, 110, 95, 105, 122), null),
                Arguments.of("small slice: +4 tickets is below the 5-ticket floor",
                        List.of(2, 3, 2, 3, 2, 3, 2, 3, 7), null),
                Arguments.of("small slice: +9 tickets clears the floor",
                        List.of(2, 3, 2, 3, 2, 3, 2, 3, 12), Direction.UP),
                Arguments.of("+20% on a large flat slice stays under the 25% floor",
                        List.of(1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1200), null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    void lastWeek(String name, List<Integer> series, Direction expected) {
        Assessment last = AnomalyDetector.assess(series).getLast();

        assertThat(last.anomaly()).isEqualTo(expected != null);
        assertThat(last.direction()).isEqualTo(expected);
    }

    @Test
    void anEarlierSpikeDoesNotMaskALaterOne() {
        // An outage week in the history must not widen the band so much that the
        // next problem goes unnoticed — the reason for median/MAD over mean/stddev.
        List<Integer> series = List.of(100, 100, 100, 100, 1500, 100, 100, 100, 160);

        List<Assessment> result = AnomalyDetector.assess(series);

        assertThat(result.get(4).direction()).isEqualTo(Direction.UP);
        assertThat(result.get(5).anomaly()).as("the week after the spike is normal again").isFalse();
        assertThat(result.get(8).direction()).isEqualTo(Direction.UP);
        assertThat(result.get(8).baseline()).isEqualTo(100.0);
    }

    @Test
    void noVerdictWithoutFourWeeksOfHistory() {
        List<Assessment> result = AnomalyDetector.assess(List.of(10, 10, 10, 10, 500));

        assertThat(result.subList(0, 4)).allSatisfy(a -> {
            assertThat(a.baseline()).isNull();
            assertThat(a.anomaly()).isFalse();
        });
        assertThat(result.get(4).anomaly()).isTrue();
    }

    @Test
    void baselineUsesOnlyThePreviousEightWeeks() {
        List<Integer> series = new ArrayList<>(Collections.nCopies(10, 500));
        series.addAll(Collections.nCopies(8, 100));
        series.add(100);

        assertThat(AnomalyDetector.assess(series).getLast().baseline()).isEqualTo(100.0);
    }
}
