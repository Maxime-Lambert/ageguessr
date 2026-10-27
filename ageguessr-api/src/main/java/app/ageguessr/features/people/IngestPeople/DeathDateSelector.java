package app.ageguessr.features.people.IngestPeople;

import java.time.LocalDate;
import java.util.List;

/**
 * Turns a person's P570 statements into the date photos must not be later than, from
 * the statements of the best rank ({@link WikidataRank#best}). Unlike the birth, a death known only to the
 * month or year is usable: the earliest possible day is kept, which can only reject
 * more photos, never accept one taken after the death.
 */
final class DeathDateSelector {

    private DeathDateSelector() {}

    sealed interface Outcome {}

    /** No death statement: alive, as far as Wikidata knows. */
    record Alive() implements Outcome {}

    record Died(LocalDate earliestPossibleDay) implements Outcome {}

    /** Dead at an unknown or unreadable date: photos cannot be checked against it, so the person is skipped. */
    record Unusable() implements Outcome {}

    static Outcome select(List<DeathStatement> statements) {
        List<DeathStatement> retained = WikidataRank.best(statements, DeathStatement::rank);
        if (retained.isEmpty()) {
            return new Alive();
        }
        LocalDate earliest = null;
        for (DeathStatement statement : retained) {
            if (statement.time() == null || statement.precision() == null) {
                return new Unusable();
            }
            var date = PhotoDate.fromWikibase(statement.time(), statement.precision());
            if (date.isEmpty()) {
                return new Unusable();
            }
            if (earliest == null || date.get().from().isBefore(earliest)) {
                earliest = date.get().from();
            }
        }
        return new Died(earliest);
    }
}
