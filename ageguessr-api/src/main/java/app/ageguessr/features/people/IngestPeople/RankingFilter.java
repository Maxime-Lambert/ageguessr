package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.WikidataId;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Who the QLever rankings list: born from {@code minBirthYear} (and up to
 * {@code maxBirthYear} when set), without any of {@code excludedOccupations}, with one of
 * {@code onlyOccupations} when that is not empty. Filtering in the ranking means a
 * person left out is never fetched nor looked at.
 */
record RankingFilter(
        int minBirthYear, OptionalInt maxBirthYear, Set<String> excludedOccupations, Set<String> onlyOccupations) {

    RankingFilter {
        excludedOccupations = Set.copyOf(excludedOccupations);
        onlyOccupations = Set.copyOf(onlyOccupations);
        Stream.concat(excludedOccupations.stream(), onlyOccupations.stream())
                .filter(qid -> !WikidataId.isValid(qid))
                .findFirst()
                .ifPresent(qid -> {
                    throw new IllegalArgumentException("not a Wikidata QID: " + qid);
                });
    }

    /** Only the birth year floor, as before any focus. */
    static RankingFilter bornSince(int minBirthYear) {
        return new RankingFilter(minBirthYear, OptionalInt.empty(), Set.of(), Set.of());
    }

    /**
     * The SPARQL lines to add to a ranking that binds {@code ?person} and {@code ?birth},
     * after its own minimum birth year. Empty when there is nothing more to filter, so
     * that an unfocused ranking stays the query it always was.
     */
    String clauses() {
        StringBuilder clauses = new StringBuilder();
        maxBirthYear.ifPresent(year -> clauses.append("FILTER(YEAR(?birth) <= ").append(year).append(")\n"));
        if (!onlyOccupations.isEmpty()) {
            clauses.append("VALUES ?occupation { ").append(entities(onlyOccupations)).append(" }\n")
                    .append("?person wdt:P106 ?occupation .\n");
        }
        if (!excludedOccupations.isEmpty()) {
            clauses.append("FILTER NOT EXISTS { VALUES ?excluded { ")
                    .append(entities(excludedOccupations))
                    .append(" } ?person wdt:P106 ?excluded . }\n");
        }
        return clauses.toString();
    }

    /** Sorted, so that a query is always the same text. */
    private static String entities(Set<String> qids) {
        return qids.stream().sorted().map(q -> "wd:" + q).collect(Collectors.joining(" "));
    }
}
