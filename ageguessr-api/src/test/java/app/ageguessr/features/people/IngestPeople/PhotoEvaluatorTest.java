package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.PhotoDateSource;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PhotoEvaluatorTest {

    private static final LocalDate BIRTH = LocalDate.of(1950, 3, 10);
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 26);

    @Test
    void acceptsADayCorroboratedByTheExif() {
        PhotoEvaluation result = evaluate(evidence("Portrait.jpg", Optional.empty(), "1976-07-14", "1976:07:14 15:30:00"));

        assertThat(result)
                .isEqualTo(new PhotoEvaluation.Accepted(
                        PhotoDate.ofDay(LocalDate.of(1976, 7, 14)), PhotoDateSource.INFORMATION_TEMPLATE, 26));
    }

    @Test
    void acceptsAStructuredDateAlone() {
        PhotoDate july = PhotoDate.ofMonth(YearMonth.of(1976, 7));

        assertThat(evaluate(evidence("Portrait.jpg", Optional.of(july), null, "1976:07:20 10:00:00")))
                .isEqualTo(new PhotoEvaluation.Accepted(july, PhotoDateSource.STRUCTURED_DATA, 26));
    }

    @Test
    void keepsTheIntersectionWhenBothSourcesAgree() {
        PhotoEvaluation result = evaluate(
                evidence("Portrait.jpg", Optional.of(PhotoDate.ofYear(1976)), "1976-07-14", "1976:07:14 15:30:00"));

        assertThat(result)
                .isEqualTo(new PhotoEvaluation.Accepted(
                        PhotoDate.ofDay(LocalDate.of(1976, 7, 14)), PhotoDateSource.BOTH, 26));
    }

    @Test
    void rejectsSourcesThatContradictEachOther() {
        assertRejected(
                evidence("Portrait.jpg", Optional.of(PhotoDate.ofYear(1977)), "1976-07-14", "1976:07:14 15:30:00"),
                PhotoRejection.CONFLICTING_DATES);
    }

    @Test
    void rejectsAPhotoWithoutAnyDate() {
        assertRejected(evidence("Portrait.jpg", Optional.empty(), "Unknown date", "1976:07:14 15:30:00"),
                PhotoRejection.NOT_DATED);
    }

    @Test
    void rejectsAPhotoBeforeTheBirth() {
        assertRejected(evidence("Portrait.jpg", Optional.empty(), "1950-03-09", "1950:03:09 12:00:00"),
                PhotoRejection.OUTSIDE_LIFETIME);
    }

    @Test
    void acceptsAPhotoOnTheDayOfBirth() {
        assertThat(evaluate(evidence("Portrait.jpg", Optional.empty(), "1950-03-10", "1950:03:10 12:00:00")))
                .isInstanceOf(PhotoEvaluation.Accepted.class);
    }

    @Test
    void rejectsAYearThatStartsBeforeTheBirth() {
        // Born 1950-03-10: "1950" also covers January and February, before the birth.
        assertRejected(evidence("Portrait.jpg", Optional.empty(), "1950", "1950:06:01 12:00:00"),
                PhotoRejection.OUTSIDE_LIFETIME);
    }

    @Test
    void rejectsAPhotoAfterTheDeath() {
        LocalDate death = LocalDate.of(1990, 5, 1);

        assertThat(PhotoEvaluator.evaluate(
                        evidence("Grave.jpg", Optional.empty(), "1990-05-02", "1990:05:02 12:00:00"), BIRTH, death, TODAY))
                .isEqualTo(new PhotoEvaluation.Rejected(PhotoRejection.OUTSIDE_LIFETIME));
    }

    @Test
    void acceptsAPhotoOnTheDayOfDeath() {
        LocalDate death = LocalDate.of(1990, 5, 1);

        assertThat(PhotoEvaluator.evaluate(
                        evidence("Portrait.jpg", Optional.empty(), "1990-05-01", "1990:05:01 12:00:00"), BIRTH, death, TODAY))
                .isInstanceOf(PhotoEvaluation.Accepted.class);
    }

    @Test
    void rejectsAMonthThatEndsAfterTheDeath() {
        LocalDate death = LocalDate.of(1990, 5, 15);

        assertThat(PhotoEvaluator.evaluate(
                        evidence("Portrait.jpg", Optional.empty(), "1990-05", "1990:05:10 12:00:00"), BIRTH, death, TODAY))
                .isEqualTo(new PhotoEvaluation.Rejected(PhotoRejection.OUTSIDE_LIFETIME));
    }

    @Test
    void rejectsAPhotoInTheFuture() {
        assertRejected(evidence("Portrait.jpg", Optional.empty(), "2026-10-27", "2026:10:27 12:00:00"),
                PhotoRejection.OUTSIDE_LIFETIME);
    }

    @Test
    void acceptsAPhotoTakenToday() {
        assertThat(evaluate(evidence("Portrait.jpg", Optional.empty(), "2026-10-26", "2026:10:26 08:00:00")))
                .isEqualTo(new PhotoEvaluation.Accepted(
                        PhotoDate.ofDay(TODAY), PhotoDateSource.INFORMATION_TEMPLATE, 76));
    }

    @Test
    void rejectsAnImageDatedBeforePhotographyExisted() {
        LocalDate birth = LocalDate.of(1800, 1, 1);

        assertThat(PhotoEvaluator.evaluate(
                        evidence("Portrait.jpg", Optional.empty(), "1838-06-01", "1838:06:01 12:00:00"),
                        birth,
                        LocalDate.of(1850, 1, 1),
                        TODAY))
                .isEqualTo(new PhotoEvaluation.Rejected(PhotoRejection.BEFORE_PHOTOGRAPHY));
    }

    @Test
    void acceptsAPhotographFromTheFirstYearOfPhotography() {
        LocalDate birth = LocalDate.of(1800, 1, 1);

        assertThat(PhotoEvaluator.evaluate(
                        evidence("Portrait.jpg", Optional.empty(), "1839-06-01", "1839:06:01 12:00:00"),
                        birth,
                        LocalDate.of(1850, 1, 1),
                        TODAY))
                .isEqualTo(new PhotoEvaluation.Accepted(
                        PhotoDate.ofDay(LocalDate.of(1839, 6, 1)), PhotoDateSource.INFORMATION_TEMPLATE, 39));
    }

    @Test
    void rejectsAYearDuringWhichTheBirthdayFalls() {
        // Born 1950-03-10: during 1976 the person is 25, then 26.
        assertRejected(evidence("Portrait.jpg", Optional.empty(), "1976", "1976:07:14 15:30:00"),
                PhotoRejection.AMBIGUOUS_AGE);
    }

    @Test
    void rejectsTheMonthOfTheBirthday() {
        assertRejected(evidence("Portrait.jpg", Optional.empty(), "1976-03", "1976:03:20 15:30:00"),
                PhotoRejection.AMBIGUOUS_AGE);
    }

    @Test
    void acceptsAMonthOtherThanTheBirthdayMonth() {
        assertThat(evaluate(evidence("Portrait.jpg", Optional.empty(), "1976-04", "1976:04:20 15:30:00")))
                .isEqualTo(new PhotoEvaluation.Accepted(
                        PhotoDate.ofMonth(YearMonth.of(1976, 4)), PhotoDateSource.INFORMATION_TEMPLATE, 26));
    }

    @Test
    void acceptsAYearWhenTheBirthdayIsJanuaryFirst() {
        LocalDate birth = LocalDate.of(1950, 1, 1);

        assertThat(PhotoEvaluator.evaluate(
                        evidence("Portrait.jpg", Optional.empty(), "1976", "1976:07:14 15:30:00"), birth, null, TODAY))
                .isEqualTo(new PhotoEvaluation.Accepted(
                        PhotoDate.ofYear(1976), PhotoDateSource.INFORMATION_TEMPLATE, 26));
    }

    @Test
    void theAgeChangesOnTheBirthdayItself() {
        assertThat(evaluate(evidence("Portrait.jpg", Optional.empty(), "1976-03-09", "1976:03:09 12:00:00")))
                .extracting(r -> ((PhotoEvaluation.Accepted) r).age())
                .isEqualTo(25);
        assertThat(evaluate(evidence("Portrait.jpg", Optional.empty(), "1976-03-10", "1976:03:10 12:00:00")))
                .extracting(r -> ((PhotoEvaluation.Accepted) r).age())
                .isEqualTo(26);
    }

    @Test
    void aFebruaryTwentyNinthBirthTurnsOneOnMarchFirstOfANonLeapYear() {
        LocalDate leapDay = LocalDate.of(2000, 2, 29);

        assertThat(PhotoEvaluator.ageOn(leapDay, LocalDate.of(2001, 2, 28))).isZero();
        assertThat(PhotoEvaluator.ageOn(leapDay, LocalDate.of(2001, 3, 1))).isEqualTo(1);
        assertThat(PhotoEvaluator.ageOn(leapDay, LocalDate.of(2004, 2, 29))).isEqualTo(4);
    }

    @Test
    void rejectsAnAgeAboveOneHundredTwenty() {
        LocalDate birth = LocalDate.of(1880, 1, 1);

        assertThat(PhotoEvaluator.evaluate(
                        evidence("Portrait.jpg", Optional.empty(), "2001-06-01", "2001:06:01 12:00:00"), birth, null, TODAY))
                .isEqualTo(new PhotoEvaluation.Rejected(PhotoRejection.AGE_OUT_OF_RANGE));
    }

    @Test
    void acceptsAnAgeOfExactlyOneHundredTwenty() {
        LocalDate birth = LocalDate.of(1880, 1, 1);

        assertThat(PhotoEvaluator.evaluate(
                        evidence("Portrait.jpg", Optional.empty(), "2000-06-01", "2000:06:01 12:00:00"), birth, null, TODAY))
                .extracting(r -> ((PhotoEvaluation.Accepted) r).age())
                .isEqualTo(120);
    }

    @Test
    void rejectsADateContradictedByAYearInTheFileName() {
        // The scan-date trap found in the spike (fixture commons-imageinfo-scan-date-trap.json).
        LocalDate birth = LocalDate.of(1936, 8, 31);

        assertThat(PhotoEvaluator.evaluate(
                        evidence(
                                "File:Marie-Thérèse Kerschbaumer (1988) by Guenter Prust.jpg",
                                Optional.empty(),
                                "2012-11-26 04:53:09",
                                "2012:11:26 04:53:09"),
                        birth,
                        null,
                        TODAY))
                .isEqualTo(new PhotoEvaluation.Rejected(PhotoRejection.FILE_NAME_YEAR_CONFLICT));
    }

    @Test
    void acceptsAFileNameNamingTheRightYearAmongOthers() {
        assertThat(evaluate(evidence(
                        "Concert 1976 (scanned 2012).jpg", Optional.empty(), "1976-07-14", "1976:07:14 15:30:00")))
                .isInstanceOf(PhotoEvaluation.Accepted.class);
    }

    @Test
    void ignoresNumbersInTheFileNameThatCannotBePhotoYears() {
        assertThat(evaluate(evidence("IMG_1234 DSC_2099 1700.jpg", Optional.empty(), "1976-07-14", "1976:07:14 15:30:00")))
                .isInstanceOf(PhotoEvaluation.Accepted.class);
    }

    @Test
    void countsFileNameYearsFromTheFirstYearOfPhotography() {
        assertThat(evaluate(evidence("Album 1838.jpg", Optional.empty(), "1976-07-14", "1976:07:14 15:30:00")))
                .isInstanceOf(PhotoEvaluation.Accepted.class);
        assertRejected(evidence("Album 1839.jpg", Optional.empty(), "1976-07-14", "1976:07:14 15:30:00"),
                PhotoRejection.FILE_NAME_YEAR_CONFLICT);
    }

    @Test
    void countsFileNameYearsUpToTheCurrentYear() {
        assertRejected(evidence("Tour 2026.jpg", Optional.empty(), "1976-07-14", "1976:07:14 15:30:00"),
                PhotoRejection.FILE_NAME_YEAR_CONFLICT);
        assertThat(evaluate(evidence("Tour 2027.jpg", Optional.empty(), "1976-07-14", "1976:07:14 15:30:00")))
                .isInstanceOf(PhotoEvaluation.Accepted.class);
    }

    @Test
    void ignoresAYearGluedToOtherDigits() {
        assertThat(evaluate(evidence("Photo 19885.jpg", Optional.empty(), "1976-07-14", "1976:07:14 15:30:00")))
                .isInstanceOf(PhotoEvaluation.Accepted.class);
    }

    @Test
    void rejectsAPhotoWithoutExifDate() {
        assertRejected(evidence("Portrait.jpg", Optional.empty(), "1976-07-14", null), PhotoRejection.NO_EXIF_DATE);
    }

    @Test
    void rejectsAnExifDateOutsideTheDeclaredDate() {
        assertRejected(evidence("Portrait.jpg", Optional.empty(), "1976-07-14", "1976:07:15 00:00:01"),
                PhotoRejection.EXIF_CONFLICT);
    }

    @Test
    void acceptsAnExifDateAnywhereInsideADeclaredMonth() {
        assertThat(evaluate(evidence("Portrait.jpg", Optional.empty(), "1976-07", "1976:07:31 23:59:59")))
                .isInstanceOf(PhotoEvaluation.Accepted.class);
    }

    private static PhotoEvaluation evaluate(PhotoEvaluator.Evidence evidence) {
        return PhotoEvaluator.evaluate(evidence, BIRTH, null, TODAY);
    }

    private static void assertRejected(PhotoEvaluator.Evidence evidence, PhotoRejection reason) {
        assertThat(evaluate(evidence)).isEqualTo(new PhotoEvaluation.Rejected(reason));
    }

    private static PhotoEvaluator.Evidence evidence(
            String file, Optional<PhotoDate> structured, String information, String exif) {
        return new PhotoEvaluator.Evidence(file, structured, information, exif);
    }
}
