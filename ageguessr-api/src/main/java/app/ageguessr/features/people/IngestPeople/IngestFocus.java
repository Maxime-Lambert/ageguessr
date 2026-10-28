package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.WikidataId;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * What one ingestion wave aims at, from the command line: occupations (Wikidata P106)
 * to leave out or to keep only, balance cells to walk, age bands to keep photos in. An
 * empty set means no restriction. A later wave can thus fill what the pool lacks and
 * skip what it has too much of (footballers after the first production run).
 */
record IngestFocus(
        Set<String> excludedOccupations, Set<String> onlyOccupations, Set<Cell> cells, Set<String> ageBands) {

    static final IngestFocus NONE = new IngestFocus(Set.of(), Set.of(), Set.of(), Set.of());

    IngestFocus {
        excludedOccupations = Set.copyOf(excludedOccupations);
        onlyOccupations = Set.copyOf(onlyOccupations);
        cells = Set.copyOf(cells);
        ageBands = Set.copyOf(ageBands);
        // Checked here so that a typo fails before the run starts; RankingFilter checks
        // again, as the QIDs end up in the text of a SPARQL query.
        Stream.concat(excludedOccupations.stream(), onlyOccupations.stream())
                .filter(qid -> !WikidataId.isValid(qid))
                .findFirst()
                .ifPresent(qid -> {
                    throw new IllegalArgumentException("not a Wikidata QID: " + qid);
                });
        ageBands.forEach(AgeBand::start);
        Set<String> both = new HashSet<>(excludedOccupations);
        both.retainAll(onlyOccupations);
        if (!both.isEmpty()) {
            throw new IllegalArgumentException("occupations both excluded and kept only: " + both);
        }
    }

    boolean isNone() {
        return equals(NONE);
    }

    boolean includes(Cell cell) {
        return cells.isEmpty() || cells.contains(cell);
    }

    boolean wantsAge(int age) {
        return ageBands.isEmpty() || ageBands.contains(AgeBand.of(age));
    }

    /** The youngest age a wanted photo can show; empty when every age is wanted. */
    OptionalInt youngestAge() {
        return ageBands.stream().mapToInt(AgeBand::start).min();
    }
}
