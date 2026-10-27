package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * One box of the pool balance: a continent of nationality and a gender. The target is
 * shared equally between the male and female cells; the OTHER cells get the same cap,
 * which they rarely reach, so they are in practice taken whole.
 */
record Cell(Continent continent, Gender gender) {

    /** The cells the target is divided between: 6 continents × men and women. */
    static final int SHARING_THE_TARGET = Continent.values().length * 2;

    /** Every cell, in the order the balanced ingestion visits them. */
    static final List<Cell> ALL = Arrays.stream(Continent.values())
            .flatMap(continent -> Arrays.stream(Gender.values()).map(gender -> new Cell(continent, gender)))
            .toList();

    /** Empty when Wikidata does not tell the gender or the continent: such a person counts nowhere. */
    static Optional<Cell> of(PersonRecord person) {
        return person.gender() == null || person.continent() == null
                ? Optional.empty()
                : Optional.of(new Cell(person.continent(), person.gender()));
    }

    @Override
    public String toString() {
        return continent + "/" + gender;
    }
}
