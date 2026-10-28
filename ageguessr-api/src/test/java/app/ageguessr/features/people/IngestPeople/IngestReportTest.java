package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import app.ageguessr.features.people.PhotoDateSource;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IngestReportTest {

    @Test
    void averagesPhotosPerKeptPersonAndKilobytesPerImage() {
        IngestReport report = new IngestReport();
        report.personKept(person("Q1"), List.of(photo(4096), photo(4096)));
        report.personKept(person("Q2"), List.of(photo(4096)));

        // 3 photos for 2 people; 12 KB for 3 images.
        assertThat(report.toString()).contains("photos per kept person=1.50", "KB per image=4");
    }

    @Test
    void printsDashesBeforeAnythingIsKept() {
        assertThat(new IngestReport().toString()).contains("photos per kept person=-", "KB per image=-");
    }

    @Test
    void printsTheBalanceOnlyForABalancedRun() {
        IngestReport report = new IngestReport();
        assertThat(report.toString()).doesNotContain("balance");

        report.cellStatus(new Cell(Continent.ASIA, Gender.FEMALE), 3, 5, true);

        assertThat(report.toString()).contains("balance (photos/quota)={ASIA/FEMALE=3/5 exhausted}");
    }

    @Test
    void countsNoPhotoLeftOutWhenNoneWas() {
        IngestReport report = new IngestReport();
        report.photosNotSelected(0);

        assertThat(report.photosRejected()).isEmpty();
    }

    @Test
    void ranksTheOccupationsOfKeptPeopleMostFrequentFirst() {
        IngestReport report = new IngestReport();
        report.personKept(person("Q1", "Q937857"), List.of());
        report.personKept(person("Q2", "Q937857", "Q82955"), List.of());
        report.personKept(person("Q3", "Q33999"), List.of());

        // Q33999 and Q82955 tie at 1: the QID breaks the tie.
        assertThat(report.topOccupations())
                .containsExactly(entry("Q937857", 2), entry("Q33999", 1), entry("Q82955", 1));
        assertThat(report.toString()).contains("kept people by occupation (top 15)={Q937857=2, Q33999=1, Q82955=1}");
    }

    @Test
    void keepsOnlyTheFifteenMostFrequentOccupations() {
        IngestReport report = new IngestReport();
        for (int i = 1; i <= IngestReport.TOP_OCCUPATIONS + 1; i++) {
            report.personKept(person("Q" + i, "Q" + (100 + i)), List.of());
        }
        report.personKept(person("Q99", "Q116"), List.of());

        assertThat(report.topOccupations()).hasSize(IngestReport.TOP_OCCUPATIONS).containsKey("Q116");
    }

    private static PersonRecord person(String qid, String... occupations) {
        return new PersonRecord(
                qid, 1, "Jane", null, Set.of(), List.of(), List.of(), Gender.FEMALE, Continent.ASIA, Set.of(occupations));
    }

    private static NewPhoto photo(int bytes) {
        LocalDate day = LocalDate.of(1990, 7, 14);
        return new NewPhoto(
                "A.jpg",
                new PhotoEvaluation.Accepted(PhotoDate.ofDay(day), PhotoDateSource.BOTH, 40),
                "CC0",
                null,
                null,
                "https://commons.wikimedia.org/wiki/File:A.jpg",
                new ImageReencoder.Reencoded(new byte[bytes], 800, 1000));
    }
}
