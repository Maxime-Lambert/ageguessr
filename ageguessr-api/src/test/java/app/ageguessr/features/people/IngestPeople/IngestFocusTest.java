package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IngestFocusTest {

    private static final Cell AFRICA_WOMEN = new Cell(Continent.AFRICA, Gender.FEMALE);

    @Test
    void noFocusIncludesEveryCellAndEveryAge() {
        assertThat(IngestFocus.NONE.includes(AFRICA_WOMEN)).isTrue();
        assertThat(IngestFocus.NONE.wantsAge(18)).isTrue();
        assertThat(IngestFocus.NONE.wantsAge(95)).isTrue();
        assertThat(IngestFocus.NONE.youngestAge()).isEmpty();
        assertThat(IngestFocus.NONE.isNone()).isTrue();
    }

    @Test
    void keepsOnlyTheGivenCells() {
        IngestFocus focus = new IngestFocus(Set.of(), Set.of(), Set.of(AFRICA_WOMEN), Set.of());

        assertThat(focus.includes(AFRICA_WOMEN)).isTrue();
        assertThat(focus.includes(new Cell(Continent.AFRICA, Gender.MALE))).isFalse();
        assertThat(focus.isNone()).isFalse();
    }

    @Test
    void keepsOnlyAgesInTheGivenBands() {
        IngestFocus focus = new IngestFocus(Set.of(), Set.of(), Set.of(), Set.of("82+", "74-81"));

        assertThat(focus.wantsAge(73)).isFalse();
        assertThat(focus.wantsAge(74)).isTrue();
        assertThat(focus.wantsAge(90)).isTrue();
        assertThat(focus.youngestAge()).hasValue(74);
    }

    @Test
    void refusesAnOccupationThatIsNotAQid() {
        assertThatThrownBy(() -> new IngestFocus(Set.of("Q1 } ?x ?y ?z"), Set.of(), Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a Wikidata QID");
        assertThatThrownBy(() -> new IngestFocus(Set.of(), Set.of("footballer"), Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAnUnknownAgeBand() {
        assertThatThrownBy(() -> new IngestFocus(Set.of(), Set.of(), Set.of(), Set.of("70-80")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown age band");
    }

    @Test
    void refusesAnOccupationBothExcludedAndKeptOnly() {
        assertThatThrownBy(() -> new IngestFocus(Set.of("Q937857"), Set.of("Q937857"), Set.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Q937857");
    }
}
