package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class IngestPeopleRunnerTest {

    @Test
    void usesTheConfiguredTargetByDefault() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments(), 3000))
                .isEqualTo(new IngestPeopleCommand(3000, null, 0, 0, false, IngestFocus.NONE));
    }

    @Test
    void readsTheLimit() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments("--limit=30"), 3000))
                .isEqualTo(new IngestPeopleCommand(30, null, 0, 0, false, IngestFocus.NONE));
    }

    @Test
    void readsASingleQid() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments("--qid=Q42"), 3000))
                .isEqualTo(new IngestPeopleCommand(3000, "Q42", 0, 0, false, IngestFocus.NONE));
    }

    @Test
    void readsTheOffsetAndTheDryRunFlag() {
        assertThat(IngestPeopleRunner.command(
                        new DefaultApplicationArguments("--offset=10000", "--sample=200", "--dry-run"), 3000))
                .isEqualTo(new IngestPeopleCommand(3000, null, 10000, 200, true, IngestFocus.NONE));
    }

    @Test
    void acceptsAnOffsetOfZero() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments("--offset=0", "--sample=5"), 3000)
                        .offset())
                .isZero();
    }

    @Test
    void readsTheFocusOptions() {
        IngestPeopleCommand command = IngestPeopleRunner.command(
                new DefaultApplicationArguments(
                        "--exclude-occupations=Q937857, Q2066131",
                        "--only-occupations=Q36180",
                        "--cells=AFRICA/FEMALE,europe/other",
                        "--age-bands=74-81,82+"),
                3000);

        assertThat(command.focus())
                .isEqualTo(new IngestFocus(
                        Set.of("Q937857", "Q2066131"),
                        Set.of("Q36180"),
                        Set.of(new Cell(Continent.AFRICA, Gender.FEMALE), new Cell(Continent.EUROPE, Gender.OTHER)),
                        Set.of("74-81", "82+")));
    }

    @Test
    void refusesAMalformedFocus() {
        for (String option : List.of(
                "--exclude-occupations=Q1 }",
                "--only-occupations=actor",
                "--cells=ATLANTIS/FEMALE",
                "--cells=AFRICA",
                "--age-bands=70-80",
                "--age-bands=,")) {
            assertThatThrownBy(() -> IngestPeopleRunner.command(new DefaultApplicationArguments(option), 3000))
                    .as(option)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void refusesAFocusForASinglePerson() {
        assertThatThrownBy(() -> IngestPeopleRunner.command(
                        new DefaultApplicationArguments("--qid=Q42", "--exclude-occupations=Q937857"), 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesCellsForTheSampleWalkButKeepsOccupationsAndAges() {
        assertThatThrownBy(() -> IngestPeopleRunner.command(
                        new DefaultApplicationArguments("--sample=200", "--cells=AFRICA/FEMALE"), 3000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(IngestPeopleRunner.command(
                                new DefaultApplicationArguments(
                                        "--sample=200", "--exclude-occupations=Q937857", "--age-bands=82+"),
                                3000)
                        .focus()
                        .isNone())
                .isFalse();
    }

    @Test
    void refusesAnOffsetWithoutASample() {
        assertThatThrownBy(() -> IngestPeopleRunner.command(new DefaultApplicationArguments("--offset=10"), 3000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDryRunAloneIsABalancedDryRun() {
        assertThat(IngestPeopleRunner.command(new DefaultApplicationArguments("--limit=60", "--dry-run"), 3000))
                .isEqualTo(new IngestPeopleCommand(60, null, 0, 0, true, IngestFocus.NONE));
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
