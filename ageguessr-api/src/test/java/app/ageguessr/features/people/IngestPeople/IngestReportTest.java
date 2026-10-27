package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static PersonRecord person(String qid) {
        return new PersonRecord(qid, 1, "Jane", null, Set.of(), List.of(), List.of(), Gender.FEMALE, Continent.ASIA);
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
