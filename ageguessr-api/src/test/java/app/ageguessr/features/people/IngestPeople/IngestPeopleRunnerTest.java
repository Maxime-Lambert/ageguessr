package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class IngestPeopleRunnerTest {

    @Test
    void usesTheConfiguredTargetByDefault() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments(), 3000))
                .isEqualTo(new IngestPeopleCommand(3000, null, 0, 0, false));
    }

    @Test
    void readsTheLimit() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments("--limit=30"), 3000))
                .isEqualTo(new IngestPeopleCommand(30, null, 0, 0, false));
    }

    @Test
    void readsASingleQid() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments("--qid=Q42"), 3000))
                .isEqualTo(new IngestPeopleCommand(3000, "Q42", 0, 0, false));
    }

    @Test
    void readsTheOffsetAndTheDryRunFlag() {
        assertThat(IngestPeopleRunner.command(
                        new DefaultApplicationArguments("--offset=10000", "--sample=200", "--dry-run"), 3000))
                .isEqualTo(new IngestPeopleCommand(3000, null, 10000, 200, true));
    }

    @Test
    void acceptsAnOffsetOfZero() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments("--offset=0", "--sample=5"), 3000)
                        .offset())
                .isZero();
    }

    @Test
    void refusesAnOffsetWithoutASample() {
        assertThatThrownBy(() -> IngestPeopleRunner.command(new DefaultApplicationArguments("--offset=10"), 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDryRunAloneIsABalancedDryRun() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments("--limit=60", "--dry-run"), 3000))
                .isEqualTo(new IngestPeopleCommand(60, null, 0, 0, true));
    }

    @Test
    void refusesANegativeOffset() {
        assertThatThrownBy(() -> IngestPeopleRunner.command(new DefaultApplicationArguments("--offset=-1", "--sample=5"), 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAnOffsetThatIsNotANumber() {
        assertThatThrownBy(() -> IngestPeopleRunner.command(new DefaultApplicationArguments("--offset=ten", "--sample=5"), 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesANonPositiveSample() {
        assertThatThrownBy(() -> IngestPeopleRunner.command(new DefaultApplicationArguments("--sample=0"), 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesASampleWithASingleQid() {
        assertThatThrownBy(() -> IngestPeopleRunner.command(
                        new DefaultApplicationArguments("--qid=Q42", "--sample=10"), 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesAnOffsetWithASingleQid() {
        assertThatThrownBy(() -> IngestPeopleRunner.command(
                        new DefaultApplicationArguments("--qid=Q42", "--offset=10"), 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesANonPositiveLimit() {
        assertThatThrownBy(() -> IngestPeopleRunner.command(new DefaultApplicationArguments("--limit=0"), 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesARepeatedOption() {
        assertThatThrownBy(() -> IngestPeopleRunner.command(
                        new DefaultApplicationArguments("--qid=Q1", "--qid=Q2"), 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
