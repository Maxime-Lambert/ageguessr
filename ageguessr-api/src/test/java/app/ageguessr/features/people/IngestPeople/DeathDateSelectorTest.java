package app.ageguessr.features.people.IngestPeople;

import static app.ageguessr.features.people.IngestPeople.BirthStatement.Rank.DEPRECATED;
import static app.ageguessr.features.people.IngestPeople.BirthStatement.Rank.NORMAL;
import static app.ageguessr.features.people.IngestPeople.BirthStatement.Rank.PREFERRED;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeathDateSelectorTest {

    @Test
    void noStatementMeansAlive() {
        assertThat(DeathDateSelector.select(List.of())).isEqualTo(new DeathDateSelector.Alive());
    }

    @Test
    void onlyDeprecatedStatementsMeansAlive() {
        assertThat(DeathDateSelector.select(List.of(new DeathStatement("1990-05-01T00:00:00Z", 11, DEPRECATED))))
                .isEqualTo(new DeathDateSelector.Alive());
    }

    @Test
    void readsADayPreciseDeath() {
        assertThat(DeathDateSelector.select(List.of(new DeathStatement("1990-05-01T00:00:00Z", 11, NORMAL))))
                .isEqualTo(new DeathDateSelector.Died(LocalDate.of(1990, 5, 1)));
    }

    @Test
    void aPreferredStatementWinsOverNormalOnes() {
        assertThat(DeathDateSelector.select(List.of(
                        new DeathStatement("1882-01-12T00:00:00Z", 11, NORMAL),
                        new DeathStatement("1882-01-11T00:00:00Z", 11, PREFERRED))))
                .isEqualTo(new DeathDateSelector.Died(LocalDate.of(1882, 1, 11)));
    }

    @Test
    void keepsTheEarliestDayAmongDisagreeingStatements() {
        assertThat(DeathDateSelector.select(List.of(
                        new DeathStatement("1990-05-20T00:00:00Z", 11, NORMAL),
                        new DeathStatement("1990-05-01T00:00:00Z", 11, NORMAL))))
                .isEqualTo(new DeathDateSelector.Died(LocalDate.of(1990, 5, 1)));
    }

    @Test
    void aDeathKnownToTheYearCountsFromItsFirstDay() {
        assertThat(DeathDateSelector.select(List.of(new DeathStatement("1990-00-00T00:00:00Z", 9, NORMAL))))
                .isEqualTo(new DeathDateSelector.Died(LocalDate.of(1990, 1, 1)));
    }

    @Test
    void anUnknownDeathDateMakesThePersonUnusable() {
        assertThat(DeathDateSelector.select(List.of(new DeathStatement(null, null, NORMAL))))
                .isEqualTo(new DeathDateSelector.Unusable());
    }

    @Test
    void aDeathKnownToTheDecadeMakesThePersonUnusable() {
        assertThat(DeathDateSelector.select(List.of(new DeathStatement("1990-00-00T00:00:00Z", 8, NORMAL))))
                .isEqualTo(new DeathDateSelector.Unusable());
    }

    @Test
    void oneUnknownStatementAmongKnownOnesMakesThePersonUnusable() {
        assertThat(DeathDateSelector.select(List.of(
                        new DeathStatement("1990-05-01T00:00:00Z", 11, NORMAL), new DeathStatement(null, null, NORMAL))))
                .isEqualTo(new DeathDateSelector.Unusable());
    }

    @Test
    void anUnknownNormalStatementDoesNotMatterWhenAnotherIsPreferred() {
        assertThat(DeathDateSelector.select(List.of(
                        new DeathStatement(null, null, NORMAL), new DeathStatement("1990-05-01T00:00:00Z", 11, PREFERRED))))
                .isEqualTo(new DeathDateSelector.Died(LocalDate.of(1990, 5, 1)));
    }
}
