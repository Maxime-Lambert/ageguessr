package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CellTest {

    private static final Cell WOMEN = new Cell(Continent.AFRICA, Gender.FEMALE);
    private static final Cell MEN = new Cell(Continent.AFRICA, Gender.MALE);
    private static final Cell OTHER = new Cell(Continent.AFRICA, Gender.OTHER);

    @Test
    void sharesTheTargetBetweenTheTwelveMaleAndFemaleCells() {
        assertThat(WOMEN.quota(3000)).isEqualTo(250);
        assertThat(MEN.quota(3000)).isEqualTo(250);
        assertThat(WOMEN.quota(3001)).isEqualTo(251);
        // The default target: 5 000 photos, 417 per cell.
        assertThat(WOMEN.quota(5000)).isEqualTo(417);
    }

    @Test
    void givesTheOtherCellsTheirRealWeightRoundedUp() {
        // 3 000 × 0.11 % / 6 continents = 0.55 → 1.
        assertThat(OTHER.quota(3000)).isEqualTo(1);
        // 5 000 × 0.11 % / 6 = 0.92 → 1.
        assertThat(OTHER.quota(5000)).isEqualTo(1);
        // 60 000 × 0.11 % / 6 = 11.
        assertThat(OTHER.quota(60_000)).isEqualTo(11);
    }

    @Test
    void listsEighteenCells() {
        assertThat(Cell.ALL).hasSize(18).doesNotHaveDuplicates();
        assertThat(Set.copyOf(Cell.ALL)).contains(WOMEN, MEN, OTHER);
    }

    @Test
    void placesAPersonOnlyWhenGenderAndContinentAreKnown() {
        assertThat(Cell.of(person(Gender.FEMALE, Continent.AFRICA))).contains(WOMEN);
        assertThat(Cell.of(person(null, Continent.AFRICA))).isEmpty();
        assertThat(Cell.of(person(Gender.FEMALE, null))).isEmpty();
    }

    @Test
    void readsAsContinentSlashGender() {
        assertThat(WOMEN).hasToString("AFRICA/FEMALE");
    }

    private static PersonRecord person(Gender gender, Continent continent) {
        return new PersonRecord("Q1", 1, "Jane", null, Set.of(), List.of(), List.of(), gender, continent, Set.of());
    }
}
