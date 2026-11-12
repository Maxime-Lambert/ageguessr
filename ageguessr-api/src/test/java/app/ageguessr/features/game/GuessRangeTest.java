package app.ageguessr.features.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GuessRangeTest {

    @ParameterizedTest
    @CsvSource({
        // Older: the span starts one year past the guess.
        "40, 43, 41, 45", "40, 45, 41, 45", "40, 46, 46, 55", "40, 55, 46, 55", "40, 56, 56, 100",
        // Younger, mirrored.
        "40, 37, 35, 39", "40, 35, 35, 39", "40, 34, 25, 34", "40, 25, 25, 34", "40, 24, 18, 24",
    })
    void spansTheBandOnTheSideOfTheAnswer(int guess, int age, int min, int max) {
        assertThat(GuessRange.of(guess, age)).isEqualTo(new GuessRange(min, max));
    }

    @Test
    void isClampedToTheAgesAGuessMayTake() {
        assertThat(GuessRange.of(98, 100)).isEqualTo(new GuessRange(99, 100));
        assertThat(GuessRange.of(20, 18)).isEqualTo(new GuessRange(18, 19));
        assertThat(GuessRange.of(30, 20)).isEqualTo(new GuessRange(18, 24));
    }

    @Test
    void shrinksToTheLastAgeWhenTheAnswerIsOutOfReach() {
        // The database allows a photo up to 120: the range is never empty.
        assertThat(GuessRange.of(100, 104)).isEqualTo(new GuessRange(100, 100));
    }

    @Test
    void alwaysHoldsTheAnswer() {
        for (int guess = 18; guess <= 100; guess++) {
            for (int age = 18; age <= 100; age++) {
                if (guess != age) {
                    GuessRange range = GuessRange.of(guess, age);
                    assertThat(age).as("guess %d, age %d", guess, age).isBetween(range.min(), range.max());
                }
            }
        }
    }

    @Test
    void hasNoRangeForAnExactGuess() {
        assertThatThrownBy(() -> GuessRange.of(40, 40)).isInstanceOf(IllegalArgumentException.class);
    }
}
