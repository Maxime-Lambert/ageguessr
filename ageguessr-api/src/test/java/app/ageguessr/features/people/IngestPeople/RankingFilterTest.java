package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RankingFilterTest {

    @Test
    void addsNothingWithoutAFocus() {
        assertThat(RankingFilter.bornSince(1930).clauses()).isEmpty();
    }

    @Test
    void capsTheBirthYear() {
        RankingFilter filter = new RankingFilter(1930, OptionalInt.of(1952), Set.of(), Set.of());

        assertThat(filter.clauses()).isEqualTo("FILTER(YEAR(?birth) <= 1952)\n");
    }

    @Test
    void leavesOutAnyoneWithAnExcludedOccupationInSortedOrder() {
        RankingFilter filter = new RankingFilter(1930, OptionalInt.empty(), Set.of("Q937857", "Q2066131"), Set.of());

        assertThat(filter.clauses())
                .isEqualTo("FILTER NOT EXISTS { VALUES ?excluded { wd:Q2066131 wd:Q937857 } ?person wdt:P106 ?excluded . }\n");
    }

    @Test
    void keepsOnlyPeopleWithOneOfTheGivenOccupations() {
        RankingFilter filter = new RankingFilter(1930, OptionalInt.empty(), Set.of(), Set.of("Q36180"));

        assertThat(filter.clauses()).isEqualTo("VALUES ?occupation { wd:Q36180 }\n?person wdt:P106 ?occupation .\n");
    }

    @Test
    void combinesEveryClause() {
        RankingFilter filter = new RankingFilter(1930, OptionalInt.of(1960), Set.of("Q937857"), Set.of("Q36180"));

        assertThat(filter.clauses())
                .contains("YEAR(?birth) <= 1960", "VALUES ?occupation { wd:Q36180 }", "VALUES ?excluded { wd:Q937857 }");
    }

    @Test
    void refusesAnythingButAQidInTheQueryText() {
        assertThatThrownBy(() -> new RankingFilter(1930, OptionalInt.empty(), Set.of("Q1 } ?x"), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RankingFilter(1930, OptionalInt.empty(), Set.of(), Set.of("wd:Q1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
