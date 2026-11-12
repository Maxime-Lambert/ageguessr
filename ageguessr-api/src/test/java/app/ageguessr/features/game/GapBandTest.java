package app.ageguessr.features.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GapBandTest {

    @ParameterizedTest
    @CsvSource({
        "40, 41, SLIGHTLY", "40, 45, SLIGHTLY", "40, 46, CLEARLY", "40, 55, CLEARLY", "40, 56, FAR", "40, 99, FAR",
        "40, 39, SLIGHTLY", "40, 35, SLIGHTLY", "40, 34, CLEARLY", "40, 25, CLEARLY", "40, 24, FAR", "60, 18, FAR"
    })
    void bandsTheGapOnBothSides(int guess, int age, GapBand expected) {
        assertThat(GapBand.of(guess, age)).isEqualTo(expected);
    }

    @Test
    void hasNoBandForAnExactGuess() {
        assertThatThrownBy(() -> GapBand.of(40, 40)).isInstanceOf(IllegalArgumentException.class);
    }
}
