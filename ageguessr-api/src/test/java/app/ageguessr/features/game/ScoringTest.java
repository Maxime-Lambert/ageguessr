package app.ageguessr.features.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScoringTest {

    @ParameterizedTest(name = "{0} years off gives {1}")
    @CsvSource({"0, 500", "1, 452", "5, 303", "10, 184", "20, 68", "30, 25", "82, 0"})
    void fallsOffWithTheGap(int gap, int points) {
        assertThat(Scoring.points(50 + gap, 50)).isEqualTo(points);
        assertThat(Scoring.points(50, 50 + gap)).as("the side does not matter").isEqualTo(points);
    }

    @ParameterizedTest(name = "{0} years off rounds to {1}")
    @CsvSource({
        // 500 × e^(−0.7) = 248.29 and 500 × e^(−0.6) = 274.41: half-up rounding, not a floor.
        "6, 274",
        "7, 248",
        // 500 × e^(−4.6) = 5.03 and 500 × e^(−6.9) = 0.50 → 1, then 0.45 → 0.
        "46, 5",
        "69, 1",
        "70, 0"
    })
    void roundsToTheNearestPoint(int gap, int points) {
        assertThat(Scoring.points(18, 18 + gap)).isEqualTo(points);
    }

    @ParameterizedTest(name = "{0} against {1}")
    @CsvSource({"18, 100", "100, 18"})
    void isNeverNegativeAtTheEndsOfTheRange(int guess, int age) {
        assertThat(Scoring.points(guess, age)).isZero();
    }
}
