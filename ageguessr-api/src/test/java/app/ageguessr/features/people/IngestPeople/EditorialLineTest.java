package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.WikidataId;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EditorialLineTest {

    @Test
    void holdsOnlyWellFormedQidsSinceTheyGoIntoQueryText() {
        assertThat(Stream.of(
                                EditorialLine.CRIMES,
                                EditorialLine.FAR_RIGHT_ALIGNMENTS,
                                EditorialLine.FAR_RIGHT_IDEOLOGIES,
                                EditorialLine.LEADER_ROLES,
                                EditorialLine.AUTHORITARIAN_STATES)
                        .flatMap(java.util.Set::stream))
                .allMatch(WikidataId::isValid);
        assertThat(WikidataId.isValid(EditorialLine.DICTATOR)).isTrue();
        // 55 rated under 4 in 2020 on Wikidata, Libya, Afghanistan, 6 defunct states.
        assertThat(EditorialLine.AUTHORITARIAN_STATES).hasSize(63);
    }

    @Test
    void catchesConvictionsAndChargesForTheListedCrimesAndTheirSubclasses() {
        assertThat(EditorialLine.query(EditorialLine.Criterion.CONVICTED_OR_CHARGED))
                .startsWith("SELECT DISTINCT ?person WHERE {")
                .contains("wd:Q132821", "wd:Q135010", "?charge wdt:P279* ?crime")
                .contains("{ ?person wdt:P1399 ?charge } UNION { ?person wdt:P1595 ?charge }")
                .contains("?person wdt:P31 wd:Q5 .");
    }

    @Test
    void catchesDictatorsByOccupation() {
        assertThat(EditorialLine.query(EditorialLine.Criterion.DICTATOR)).contains("?person wdt:P106 wd:Q183318 .");
    }

    @Test
    void catchesMembersOfAPartyAlignedOrIdeologicallyFarRight() {
        assertThat(EditorialLine.query(EditorialLine.Criterion.FAR_RIGHT))
                .contains("?party wdt:P1387 ?alignment", "?party wdt:P1142 ?ideology", "?person wdt:P102 ?party .")
                .contains("wd:Q127869500 wd:Q204481 wd:Q23018230");
    }

    @Test
    void catchesHeadsOfStateOrGovernmentOfAuthoritarianStates() {
        assertThat(EditorialLine.query(EditorialLine.Criterion.AUTHORITARIAN_LEADER))
                .contains("VALUES ?role { wd:Q2285706 wd:Q48352 }", "?position wdt:P279* ?role")
                .contains("{ ?position wdt:P1001 ?state } UNION { ?position wdt:P17 ?state }")
                .contains("?person wdt:P39 ?position .", "wd:Q15180", "wd:Q159");
    }
}
