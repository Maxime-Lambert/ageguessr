package app.ageguessr.features.people.IngestPeople;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Picks the one birth date the game can trust, or none. The age shown as the answer is
 * computed from it, so any doubt excludes the person instead of guessing.
 */
final class BirthDateSelector {

    static final String GREGORIAN = "http://www.wikidata.org/entity/Q1985727";

    private BirthDateSelector() {}

    /**
     * Statements of the best rank ({@link WikidataRank#best}); they must
     * all say the same day, to the day, in the Gregorian calendar. A Julian date is
     * excluded rather than converted: it is rare for people who were photographed.
     */
    static Optional<LocalDate> select(List<BirthStatement> statements) {
        List<BirthStatement> retained = WikidataRank.best(statements, BirthStatement::rank);
        if (retained.isEmpty()
                || retained.stream().anyMatch(s -> s.precision() != PhotoDate.PRECISION_DAY
                        || !GREGORIAN.equals(s.calendarModel()))) {
            return Optional.empty();
        }
        Set<Optional<PhotoDate>> days = retained.stream()
                .map(s -> PhotoDate.fromWikibase(s.time(), s.precision()))
                .collect(Collectors.toSet());
        if (days.size() != 1) {
            return Optional.empty();
        }
        return days.iterator().next().map(PhotoDate::from);
    }
}
