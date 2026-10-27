package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class IngestPeopleRunnerTest {

    @Test
    void usesTheConfiguredTargetByDefault() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments(), 3000))
                .isEqualTo(new IngestPeopleCommand(3000, null));
    }

    @Test
    void readsTheLimit() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments("--limit=30"), 3000))
                .isEqualTo(new IngestPeopleCommand(30, null));
    }

    @Test
    void readsASingleQid() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments("--qid=Q42"), 3000))
                .isEqualTo(new IngestPeopleCommand(3000, "Q42"));
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
