package app.ageguessr.features.people.IngestPeople;

import static app.ageguessr.features.people.IngestPeople.WikidataRank.DEPRECATED;
import static app.ageguessr.features.people.IngestPeople.WikidataRank.NORMAL;
import static app.ageguessr.features.people.IngestPeople.WikidataRank.PREFERRED;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BirthDateSelectorTest {

    private static final String JULIAN = "http://www.wikidata.org/entity/Q1985786";

    @Test
    void keepsASingleGregorianDayPreciseDate() {
        assertThat(BirthDateSelector.select(List.of(day("1951-09-14", NORMAL)))).contains(LocalDate.of(1951, 9, 14));
    }

    @Test
    void ignoresADeprecatedStatement() {
        // Duncan Haldane (Q1002250) in the QLever fixture: a wrong 1951-11-14 kept as deprecated.
        assertThat(BirthDateSelector.select(List.of(day("1951-11-14", DEPRECATED), day("1951-09-14", NORMAL))))
                .contains(LocalDate.of(1951, 9, 14));
    }

    @Test
    void aPreferredStatementWinsOverNormalOnes() {
        assertThat(BirthDateSelector.select(List.of(day("1951-11-14", NORMAL), day("1951-09-14", PREFERRED))))
                .contains(LocalDate.of(1951, 9, 14));
    }

    @Test
    void acceptsTheSameDayStatedTwice() {
        assertThat(BirthDateSelector.select(List.of(day("1951-09-14", NORMAL), day("1951-09-14", NORMAL))))
                .contains(LocalDate.of(1951, 9, 14));
    }

    @Test
    void excludesTwoDifferentNormalDates() {
        assertThat(BirthDateSelector.select(List.of(day("1951-11-14", NORMAL), day("1951-09-14", NORMAL))))
                .isEmpty();
    }

    @Test
    void excludesTwoDifferentPreferredDates() {
        assertThat(BirthDateSelector.select(List.of(day("1951-11-14", PREFERRED), day("1951-09-14", PREFERRED))))
                .isEmpty();
    }

    @Test
    void excludesADateKnownOnlyToTheYear() {
        assertThat(BirthDateSelector.select(
                        List.of(new BirthStatement("1951-00-00T00:00:00Z", 9, BirthDateSelector.GREGORIAN, NORMAL))))
                .isEmpty();
    }

    @Test
    void excludesWhenOneRetainedStatementIsNotPreciseToTheDay() {
        assertThat(BirthDateSelector.select(List.of(
                        day("1951-09-14", NORMAL),
                        new BirthStatement("1951-09-00T00:00:00Z", 10, BirthDateSelector.GREGORIAN, NORMAL))))
                .isEmpty();
    }

    @Test
    void excludesAJulianCalendarDate() {
        assertThat(BirthDateSelector.select(List.of(new BirthStatement("1869-10-02T00:00:00Z", 11, JULIAN, NORMAL))))
                .isEmpty();
    }

    @Test
    void excludesWhenOnlyDeprecatedStatementsExist() {
        assertThat(BirthDateSelector.select(List.of(day("1951-09-14", DEPRECATED)))).isEmpty();
    }

    @Test
    void excludesWhenThereIsNoStatement() {
        assertThat(BirthDateSelector.select(List.of())).isEmpty();
    }

    @Test
    void excludesAnImpossibleDay() {
        assertThat(BirthDateSelector.select(List.of(day("1951-02-30", NORMAL)))).isEmpty();
    }

    private static BirthStatement day(String isoDay, WikidataRank rank) {
        return new BirthStatement(isoDay + "T00:00:00Z", 11, BirthDateSelector.GREGORIAN, rank);
    }
}
