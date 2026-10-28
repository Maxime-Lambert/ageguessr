package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgeBandTest {

    @Test
    void cutsBandsOfEightYearsFromEighteen() {
        assertThat(AgeBand.of(18)).isEqualTo("18-25");
        assertThat(AgeBand.of(25)).isEqualTo("18-25");
        assertThat(AgeBand.of(26)).isEqualTo("26-33");
        assertThat(AgeBand.of(74)).isEqualTo("74-81");
        assertThat(AgeBand.of(81)).isEqualTo("74-81");
    }

    @Test
    void putsEveryAgeFromEightyTwoInTheLastBand() {
        assertThat(AgeBand.of(82)).isEqualTo("82+");
        assertThat(AgeBand.of(120)).isEqualTo("82+");
    }

    @Test
    void listsTheNineBandsYoungestFirst() {
        assertThat(AgeBand.ALL)
                .containsExactly("18-25", "26-33", "34-41", "42-49", "50-57", "58-65", "66-73", "74-81", "82+");
    }

    @Test
    void startsEachBandAtItsYoungestAge() {
        assertThat(AgeBand.start("18-25")).isEqualTo(18);
        assertThat(AgeBand.start("74-81")).isEqualTo(74);
        assertThat(AgeBand.start("82+")).isEqualTo(82);
        assertThatThrownBy(() -> AgeBand.start("75-82")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasNoBandForAMinor() {
        assertThatThrownBy(() -> AgeBand.of(17)).isInstanceOf(IllegalArgumentException.class);
    }
}
