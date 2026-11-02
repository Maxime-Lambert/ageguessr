package app.ageguessr.features.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DirectionTest {

    @Test
    void saysOlderWhenThePersonIsOlderThanTheGuess() {
        assertThat(Direction.of(40, 41)).isEqualTo(Direction.OLDER);
    }

    @Test
    void saysYoungerWhenThePersonIsYoungerThanTheGuess() {
        assertThat(Direction.of(40, 39)).isEqualTo(Direction.YOUNGER);
    }

    @Test
    void hasNoDirectionForAnExactGuess() {
        assertThatThrownBy(() -> Direction.of(40, 40)).isInstanceOf(IllegalArgumentException.class);
    }
}
