package app.ageguessr.features.people.IngestPeople;

import static app.ageguessr.features.people.IngestPeople.WikidataRank.DEPRECATED;
import static app.ageguessr.features.people.IngestPeople.WikidataRank.NORMAL;
import static app.ageguessr.features.people.IngestPeople.WikidataRank.PREFERRED;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class WikidataRankTest {

    private record Statement(String value, WikidataRank rank) {}

    private static final Function<Statement, WikidataRank> RANK = Statement::rank;

    @Test
    void keepsEveryNormalStatementWhenNoneIsPreferred() {
        List<Statement> statements = List.of(new Statement("a", NORMAL), new Statement("b", NORMAL));

        assertThat(WikidataRank.best(statements, RANK)).containsExactlyElementsOf(statements);
    }

    @Test
    void dropsDeprecatedStatements() {
        assertThat(WikidataRank.best(List.of(new Statement("a", DEPRECATED), new Statement("b", NORMAL)), RANK))
                .extracting(Statement::value)
                .containsExactly("b");
    }

    @Test
    void keepsOnlyPreferredStatementsWhenThereAreSome() {
        assertThat(WikidataRank.best(
                        List.of(new Statement("a", NORMAL), new Statement("b", PREFERRED), new Statement("c", PREFERRED)),
                        RANK))
                .extracting(Statement::value)
                .containsExactly("b", "c");
    }

    @Test
    void aDeprecatedStatementIsNeverKeptEvenAlone() {
        assertThat(WikidataRank.best(List.of(new Statement("a", DEPRECATED)), RANK)).isEmpty();
    }

    @Test
    void nothingInNothingOut() {
        assertThat(WikidataRank.best(List.<Statement>of(), RANK)).isEmpty();
    }
}
