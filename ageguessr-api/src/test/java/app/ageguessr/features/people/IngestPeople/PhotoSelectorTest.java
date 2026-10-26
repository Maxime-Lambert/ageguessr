package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.IngestPeople.PhotoSelector.Candidate;
import app.ageguessr.features.people.IngestPeople.PhotoSelector.Kept;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The person is born on 1950-03-10 throughout. */
class PhotoSelectorTest {

    @Test
    void keepsPhotosOfDifferentYearsAndAges() {
        List<Candidate> selected = PhotoSelector.select(
                List.of(), List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1990, 7, 14, 40)), 5);

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("A.jpg", "B.jpg");
    }

    @Test
    void keepsOnlyOnePhotoPerYear() {
        // February 1976: 25. July 1976: 26. Different ages, same year.
        List<Candidate> selected = PhotoSelector.select(
                List.of(), List.of(onDay("A.jpg", 1976, 2, 1, 25), onDay("B.jpg", 1976, 7, 14, 26)), 5);

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("A.jpg");
    }

    @Test
    void keepsOnlyOnePhotoPerAge() {
        // July 1976 and February 1977: both 26, different years.
        List<Candidate> selected = PhotoSelector.select(
                List.of(), List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1977, 2, 1, 26)), 5);

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("A.jpg");
    }

    @Test
    void prefersTheMostPreciseDate() {
        Candidate month = candidate("A-month.jpg", false, PhotoDate.ofMonth(YearMonth.of(1976, 7)), 26);
        Candidate day = onDay("B-day.jpg", 1976, 7, 14, 26);

        assertThat(PhotoSelector.select(List.of(), List.of(month, day), 5))
                .extracting(Candidate::fileTitle)
                .containsExactly("B-day.jpg");
    }

    @Test
    void prefersTheMainWikidataImageAtEqualPrecision() {
        Candidate other = onDay("A.jpg", 1976, 7, 14, 26);
        Candidate main = candidate("B.jpg", true, PhotoDate.ofDay(LocalDate.of(1976, 7, 20)), 26);

        assertThat(PhotoSelector.select(List.of(), List.of(other, main), 5))
                .extracting(Candidate::fileTitle)
                .containsExactly("B.jpg");
    }

    @Test
    void breaksRemainingTiesByFileNameWhateverTheInputOrder() {
        Candidate a = onDay("A.jpg", 1976, 7, 14, 26);
        Candidate b = onDay("B.jpg", 1976, 7, 20, 26);

        assertThat(PhotoSelector.select(List.of(), List.of(b, a), 5)).containsExactly(a);
        assertThat(PhotoSelector.select(List.of(), List.of(a, b), 5)).containsExactly(a);
    }

    @Test
    void stopsAtTheMaximumPerPerson() {
        List<Candidate> selected = PhotoSelector.select(
                List.of(),
                List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1980, 7, 14, 30), onDay("C.jpg", 1990, 7, 14, 40)),
                2);

        assertThat(selected).hasSize(2);
    }

    @Test
    void photosAlreadyStoredUseUpYearsAgesAndRoom() {
        List<Candidate> selected = PhotoSelector.select(
                List.of(new Kept(1976, 26), new Kept(1985, 35)),
                List.of(
                        onDay("SameYear.jpg", 1976, 2, 1, 25),
                        onDay("SameAge.jpg", 1977, 2, 1, 26),
                        onDay("New1.jpg", 1990, 7, 14, 40),
                        onDay("New2.jpg", 2000, 7, 14, 50)),
                3);

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("New1.jpg");
    }

    @Test
    void keepsNothingWhenThePersonAlreadyHasTheMaximum() {
        assertThat(PhotoSelector.select(
                        List.of(new Kept(1976, 26), new Kept(1985, 35)), List.of(onDay("A.jpg", 1990, 7, 14, 40)), 2))
                .isEmpty();
    }

    @Test
    void keepsNothingWithoutCandidates() {
        assertThat(PhotoSelector.select(List.of(), List.of(), 5)).isEmpty();
    }

    private static Candidate onDay(String file, int year, int month, int day, int age) {
        return candidate(file, false, PhotoDate.ofDay(LocalDate.of(year, month, day)), age);
    }

    private static Candidate candidate(String file, boolean main, PhotoDate date, int age) {
        return new Candidate(file, main, new PhotoEvaluation.Accepted(date, PhotoDateSource.BOTH, age));
    }
}
