package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PhotoDateTest {

    @Test
    void aDayCoversOnlyThatDay() {
        PhotoDate date = PhotoDate.ofDay(LocalDate.of(1976, 7, 14));

        assertThat(date.from()).isEqualTo(LocalDate.of(1976, 7, 14));
        assertThat(date.to()).isEqualTo(LocalDate.of(1976, 7, 14));
    }

    @Test
    void aMonthCoversItsFirstToItsLastDay() {
        PhotoDate date = PhotoDate.ofMonth(YearMonth.of(2024, 2));

        assertThat(date.from()).isEqualTo(LocalDate.of(2024, 2, 1));
        assertThat(date.to()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void aYearCoversJanuaryFirstToDecemberThirtyFirst() {
        PhotoDate date = PhotoDate.ofYear(1976);

        assertThat(date.from()).isEqualTo(LocalDate.of(1976, 1, 1));
        assertThat(date.to()).isEqualTo(LocalDate.of(1976, 12, 31));
    }

    @Test
    void refusesAnIntervalEndingBeforeItStarts() {
        assertThatThrownBy(() -> new PhotoDate(LocalDate.of(2000, 1, 2), LocalDate.of(2000, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "+2015-02-09T00:00:00Z, 11, 2015-02-09, 2015-02-09",
        "2015-02-09T00:00:00Z, 11, 2015-02-09, 2015-02-09",
        "+2015-02-00T00:00:00Z, 10, 2015-02-01, 2015-02-28",
        "+1943-00-00T00:00:00Z, 9, 1943-01-01, 1943-12-31",
        "+1943-01-01T00:00:00Z, 9, 1943-01-01, 1943-12-31"
    })
    void readsWikibaseTimesAtTheirPrecision(String time, int precision, LocalDate from, LocalDate to) {
        assertThat(PhotoDate.fromWikibase(time, precision)).contains(new PhotoDate(from, to));
    }

    @ParameterizedTest
    @CsvSource({
        "+1950-00-00T00:00:00Z, 8", // decade
        "+1850-00-00T00:00:00Z, 7", // century
        "+2015-02-09T00:00:00Z, 12", // hour: never produced, refused rather than truncated
        "-0079-01-01T00:00:00Z, 11", // before the common era
        "+2015-02-30T00:00:00Z, 11", // impossible day
        "+2015-13-00T00:00:00Z, 10", // impossible month
        "not a date, 11"
    })
    void refusesWikibaseTimesItCannotPlaceInAYear(String time, int precision) {
        assertThat(PhotoDate.fromWikibase(time, precision)).isEmpty();
    }

    @Test
    void containsBothOfItsBounds() {
        PhotoDate july = PhotoDate.ofMonth(YearMonth.of(1976, 7));

        assertThat(july.contains(LocalDate.of(1976, 7, 1))).isTrue();
        assertThat(july.contains(LocalDate.of(1976, 7, 31))).isTrue();
        assertThat(july.contains(LocalDate.of(1976, 6, 30))).isFalse();
        assertThat(july.contains(LocalDate.of(1976, 8, 1))).isFalse();
    }

    @Test
    void intersectsAYearWithADayInsideIt() {
        PhotoDate day = PhotoDate.ofDay(LocalDate.of(1976, 7, 14));

        assertThat(PhotoDate.ofYear(1976).intersection(day)).contains(day);
        assertThat(day.intersection(PhotoDate.ofYear(1976))).contains(day);
    }

    @Test
    void intervalsTouchingOnOneDayIntersectOnThatDay() {
        PhotoDate first = new PhotoDate(LocalDate.of(1976, 1, 1), LocalDate.of(1976, 7, 14));
        PhotoDate second = new PhotoDate(LocalDate.of(1976, 7, 14), LocalDate.of(1976, 12, 31));

        assertThat(first.intersection(second)).contains(PhotoDate.ofDay(LocalDate.of(1976, 7, 14)));
    }

    @Test
    void disjointIntervalsHaveNoIntersection() {
        assertThat(PhotoDate.ofYear(1976).intersection(PhotoDate.ofYear(1977))).isEmpty();
        assertThat(PhotoDate.ofYear(1977).intersection(PhotoDate.ofYear(1976))).isEmpty();
    }
}
