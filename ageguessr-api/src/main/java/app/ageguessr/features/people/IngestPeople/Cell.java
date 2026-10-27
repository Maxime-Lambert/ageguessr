package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * One box of the pool balance: a continent of nationality and a gender. The target, in
 * photos in play, is shared equally between the male and female cells. The OTHER cells get their real
 * weight among people born since 1930 with a P18 image, neither more nor less (user
 * decision, 2026-10-27).
 */
record Cell(Continent continent, Gender gender) {

    /** The cells the target is divided between: 6 continents × men and women. */
    static final int SHARING_THE_TARGET = Continent.values().length * 2;

    /** ~830 people with another gender value out of 737 094 (QLever, 2026-10-27). */
    static final double OTHER_GENDER_SHARE = 0.0011;

    /**
     * How many photos in play this cell may hold for a given target. Rounded up, so that
     * each OTHER cell keeps at least one place: 1 per continent for 5 000 photos.
     */
    int quota(int targetPhotos) {
        return gender == Gender.OTHER
                ? (int) Math.ceil(targetPhotos * OTHER_GENDER_SHARE / Continent.values().length)
                : Math.ceilDiv(targetPhotos, SHARING_THE_TARGET);
    }

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
