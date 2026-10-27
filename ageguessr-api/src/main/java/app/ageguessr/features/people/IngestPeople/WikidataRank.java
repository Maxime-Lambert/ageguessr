package app.ageguessr.features.people.IngestPeople;

import java.util.List;
import java.util.function.Function;

/** Rank of a Wikidata statement, the same for every property (birth, death...). */
enum WikidataRank {
    PREFERRED,
    NORMAL,
    DEPRECATED;

    /**
     * The statements Wikidata itself would use: a deprecated statement is known to be
     * wrong and is dropped; when one is preferred, the normal ones are dropped too.
     */
    static <T> List<T> best(List<T> statements, Function<T, WikidataRank> rankOf) {
        List<T> notDeprecated =
                statements.stream().filter(s -> rankOf.apply(s) != DEPRECATED).toList();
        boolean anyPreferred = notDeprecated.stream().anyMatch(s -> rankOf.apply(s) == PREFERRED);
        return anyPreferred
                ? notDeprecated.stream().filter(s -> rankOf.apply(s) == PREFERRED).toList()
                : notDeprecated;
    }
}
