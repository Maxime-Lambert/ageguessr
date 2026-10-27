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
    void hasNoBandForAMinor() {
        assertThatThrownBy(() -> AgeBand.of(17)).isInstanceOf(IllegalArgumentException.class);
    }
}
