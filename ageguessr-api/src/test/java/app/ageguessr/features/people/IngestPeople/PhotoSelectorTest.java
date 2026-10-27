package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.IngestPeople.PhotoSelector.Candidate;
import app.ageguessr.features.people.IngestPeople.PhotoSelector.Kept;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The person is born on 1950-03-10 throughout. */
class PhotoSelectorTest {

    @Test
    void keepsPhotosOfDifferentYearsAndAges() {
        List<Candidate> selected = PhotoSelector.select(
                List.of(), List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1990, 7, 14, 40)), 5, 1, Map.of());

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("A.jpg", "B.jpg");
    }

    @Test
    void keepsOnlyOnePhotoPerYear() {
        // February 1976: 25. July 1976: 26. Different ages, same year.
        List<Candidate> selected = PhotoSelector.select(
                List.of(), List.of(onDay("A.jpg", 1976, 2, 1, 25), onDay("B.jpg", 1976, 7, 14, 26)), 5, 1, Map.of());

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("A.jpg");
    }

    @Test
    void keepsOnlyOnePhotoPerAge() {
        // July 1976 and February 1977: both 26, different years.
        List<Candidate> selected = PhotoSelector.select(
                List.of(), List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1977, 2, 1, 26)), 5, 1, Map.of());

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("A.jpg");
    }

    @Test
    void prefersThePhotoWhoseAgeBandHoldsTheFewestPhotos() {
        // 26 and 34 are less than 10 apart: only one can stay.
        List<Candidate> candidates = List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1984, 7, 14, 34));

        assertThat(PhotoSelector.select(List.of(), candidates, 5, 10, Map.of("26-33", 5, "34-41", 2)))
                .extracting(Candidate::fileTitle)
                .containsExactly("B.jpg");
        assertThat(PhotoSelector.select(List.of(), candidates, 5, 10, Map.of("26-33", 2, "34-41", 5)))
                .extracting(Candidate::fileTitle)
                .containsExactly("A.jpg");
    }

    @Test
    void anAgeBandWithoutAnyPhotoYetComesFirst() {
        List<Candidate> candidates = List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1984, 7, 14, 34));

        assertThat(PhotoSelector.select(List.of(), candidates, 5, 10, Map.of("26-33", 1)))
                .extracting(Candidate::fileTitle)
                .containsExactly("B.jpg");
    }

    @Test
    void countsItsOwnPicksWhenChoosingTheNextOne() {
        // 18-25 is the emptiest band; once A is picked it holds 1, like 42-49, and the
        // remaining tie is broken by PREFERENCE (file name).
        List<Candidate> candidates = List.of(
                onDay("A.jpg", 1971, 7, 14, 21), onDay("B.jpg", 1971, 2, 1, 20), onDay("C.jpg", 1995, 7, 14, 45));

        assertThat(PhotoSelector.select(List.of(), candidates, 2, 10, Map.of("42-49", 1, "18-25", 0)))
                .extracting(Candidate::fileTitle)
                .containsExactly("A.jpg", "C.jpg");
    }

    @Test
    void neverGivesUpAPhotoTheGapAllowsToFavourAnAgeBand() {
        // Both fit: the band counts only order them.
        List<Candidate> candidates = List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1990, 7, 14, 40));

        assertThat(PhotoSelector.select(List.of(), candidates, 5, 10, Map.of("26-33", 100)))
                .extracting(Candidate::fileTitle)
                .containsExactly("B.jpg", "A.jpg");
    }

    @Test
    void prefersTheMostPreciseDate() {
        Candidate month = candidate("A-month.jpg", false, PhotoDate.ofMonth(YearMonth.of(1976, 7)), 26);
        Candidate day = onDay("B-day.jpg", 1976, 7, 14, 26);

        assertThat(PhotoSelector.select(List.of(), List.of(month, day), 5, 1, Map.of()))
                .extracting(Candidate::fileTitle)
                .containsExactly("B-day.jpg");
    }

    @Test
    void prefersTheMainWikidataImageAtEqualPrecision() {
        Candidate other = onDay("A.jpg", 1976, 7, 14, 26);
        Candidate main = candidate("B.jpg", true, PhotoDate.ofDay(LocalDate.of(1976, 7, 20)), 26);

        assertThat(PhotoSelector.select(List.of(), List.of(other, main), 5, 1, Map.of()))
                .extracting(Candidate::fileTitle)
                .containsExactly("B.jpg");
    }

    @Test
    void breaksRemainingTiesByFileNameWhateverTheInputOrder() {
        Candidate a = onDay("A.jpg", 1976, 7, 14, 26);
        Candidate b = onDay("B.jpg", 1976, 7, 20, 26);

        assertThat(PhotoSelector.select(List.of(), List.of(b, a), 5, 1, Map.of())).containsExactly(a);
        assertThat(PhotoSelector.select(List.of(), List.of(a, b), 5, 1, Map.of())).containsExactly(a);
    }

    @Test
    void stopsAtTheMaximumPerPerson() {
        List<Candidate> selected = PhotoSelector.select(
                List.of(),
                List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1980, 7, 14, 30), onDay("C.jpg", 1990, 7, 14, 40)),
                2,
                1, Map.of());

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
                3,
                1, Map.of());

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("New1.jpg");
    }

    @Test
    void keepsNothingWhenThePersonAlreadyHasTheMaximum() {
        assertThat(PhotoSelector.select(
                        List.of(new Kept(1976, 26), new Kept(1985, 35)), List.of(onDay("A.jpg", 1990, 7, 14, 40)), 2, 1, Map.of()))
                .isEmpty();
    }

    @Test
    void keepsNothingWithoutCandidates() {
        assertThat(PhotoSelector.select(List.of(), List.of(), 5, 1, Map.of())).isEmpty();
    }

    @Test
    void keepsPhotosExactlyTheMinimumGapApart() {
        List<Candidate> selected = PhotoSelector.select(
                List.of(), List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1986, 7, 14, 36)), 5, 10, Map.of());

        assertThat(selected).hasSize(2);
    }

    @Test
    void dropsAPhotoOneYearShortOfTheMinimumGap() {
        List<Candidate> selected = PhotoSelector.select(
                List.of(), List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1985, 7, 14, 35)), 5, 10, Map.of());

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("A.jpg");
    }

    @Test
    void theGapMustHoldInAgeAsWellAsInYear() {
        // July 1976 (26) and February 1986 (35): ten calendar years, but only nine in age.
        List<Candidate> selected = PhotoSelector.select(
                List.of(), List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1986, 2, 1, 35)), 5, 10, Map.of());

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("A.jpg");
    }

    @Test
    void theGapMustHoldInYearAsWellAsInAge() {
        // Only the age differs by ten: inconsistent on purpose, to isolate the year check.
        List<Candidate> selected = PhotoSelector.select(
                List.of(), List.of(onDay("A.jpg", 1976, 7, 14, 26), onDay("B.jpg", 1985, 7, 14, 36)), 5, 10, Map.of());

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("A.jpg");
    }

    @Test
    void theGapAppliesToEveryPhotoAlreadyKeptNotOnlyTheLast() {
        List<Candidate> selected = PhotoSelector.select(
                List.of(new Kept(1976, 26), new Kept(1990, 40)),
                List.of(onDay("Near1976.jpg", 1980, 7, 14, 30), onDay("Far.jpg", 2005, 7, 14, 55)),
                5,
                10, Map.of());

        assertThat(selected).extracting(Candidate::fileTitle).containsExactly("Far.jpg");
    }

    @Test
    void theGapAlsoHoldsBelowAnEarlierPhoto() {
        List<Candidate> selected = PhotoSelector.select(
                List.of(new Kept(1990, 40)), List.of(onDay("Earlier.jpg", 1981, 7, 14, 31)), 5, 10, Map.of());

        assertThat(selected).isEmpty();
    }

    @Test
    void refusesAGapBelowOneYear() {
        assertThatThrownBy(() -> PhotoSelector.select(List.of(), List.of(), 5, 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Candidate onDay(String file, int year, int month, int day, int age) {
        return candidate(file, false, PhotoDate.ofDay(LocalDate.of(year, month, day)), age);
    }

    private static Candidate candidate(String file, boolean main, PhotoDate date, int age) {
        return new Candidate(file, main, new PhotoEvaluation.Accepted(date, PhotoDateSource.BOTH, age));
    }
}
