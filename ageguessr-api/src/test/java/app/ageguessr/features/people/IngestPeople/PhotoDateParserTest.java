package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Inputs below are real values seen in the spike (plans/done/people-sourcing.md, Phase 1). */
class PhotoDateParserTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "2015-02-09",
        "2015-02-09 17:32",
        "2015-02-09 19:57:53",
        "2015-02-09T19:57:53",
        "9 February 2015",
        "February 9, 2015",
        "Taken on 9 February 2015",
        "<span>2015-02-09</span>",
        "2015-02-09date QS:P571,+2015-02-09T00:00:00Z/11"
    })
    void readsADay(String raw) {
        assertThat(PhotoDateParser.parseInformationDate(raw)).contains(PhotoDate.ofDay(LocalDate.of(2015, 2, 9)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1976-07", "July 1976"})
    void readsAMonth(String raw) {
        assertThat(PhotoDateParser.parseInformationDate(raw)).contains(PhotoDate.ofMonth(YearMonth.of(1976, 7)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1943", "Taken in 1943", "Photographed in 1943", "1943date QS:P571,+1943-00-00T00:00:00Z/9"})
    void readsAYear(String raw) {
        assertThat(PhotoDateParser.parseInformationDate(raw)).contains(PhotoDate.ofYear(1943));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "",
        "Unknown date",
        "circa 1976",
        "ca. 1976",
        "c. 1976",
        "1970s",
        "16th centurydate QS:P571,+1550-00-00T00:00:00Z/7",
        "between 1707 and 1709date QS:P571,+1707-00-00T00:00:00Z/8",
        "before 1976",
        "after 1976",
        "1976-1977",
        "1976–1977",
        "1976/1977",
        "1976?",
        "~1976",
        "Moscow, early 1920s",
        "17 luglio 2015\n\n(17 July 2015 (original upload date))",
        "2015-02-30",
        "31 Smarch 2015",
        "late 1700s."
    })
    void refusesAnythingHedgedAmbiguousOrImpossible(String raw) {
        assertThat(PhotoDateParser.parseInformationDate(raw)).isEmpty();
    }

    @Test
    void readsTheDayOfAnExifDateTimeOriginal() {
        assertThat(PhotoDateParser.parseExifDate("2015:05:14 16:08:29")).contains(LocalDate.of(2015, 5, 14));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0000:00:00 00:00:00", "2015:00:00 00:00:00", "2015-05-14 16:08:29", "garbage"})
    void refusesAnExifDateWithoutAFullDay(String raw) {
        assertThat(PhotoDateParser.parseExifDate(raw)).isEmpty();
    }
}
