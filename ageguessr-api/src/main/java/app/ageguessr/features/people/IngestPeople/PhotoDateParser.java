package app.ageguessr.features.people.IngestPeople;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict parsing of the free-text dates Commons returns. Anything hedged ("circa",
 * "1970s", "between...") or that could be an upload date is refused rather than guessed:
 * a wrong date means a wrong answer in the game.
 */
final class PhotoDateParser {

    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    // {{Information}} dates rendered by Commons end with a hidden "date QS:P571,..." blob.
    private static final Pattern QUICK_STATEMENTS_SUFFIX = Pattern.compile("date QS:.*$", Pattern.DOTALL);
    private static final Pattern HEDGED = Pattern.compile(
            "circa|\\bca\\.|\\bc\\.|century|between|before|after|unknown|upload|\\d0s\\b|[?~]|\\d{4}\\s*[–/-]\\s*\\d{4}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TAKEN_PREFIX =
            Pattern.compile("^(taken|photographed) (on|in)\\s+", Pattern.CASE_INSENSITIVE);

    private static final Pattern ISO_DAY = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})([ T]\\d{2}:\\d{2}(:\\d{2})?)?$");
    private static final Pattern ISO_MONTH = Pattern.compile("^(\\d{4})-(\\d{2})$");
    private static final Pattern YEAR = Pattern.compile("^(\\d{4})$");
    private static final Pattern DAY_MONTH_YEAR = Pattern.compile("^(\\d{1,2}) ([A-Za-z]+) (\\d{4})$");
    private static final Pattern MONTH_DAY_YEAR = Pattern.compile("^([A-Za-z]+) (\\d{1,2}), (\\d{4})$");
    private static final Pattern MONTH_YEAR = Pattern.compile("^([A-Za-z]+) (\\d{4})$");

    // EXIF DateTimeOriginal: "2015:05:14 16:08:29". Unknown parts are written as zeros.
    private static final Pattern EXIF = Pattern.compile("^(\\d{4}):(\\d{2}):(\\d{2})");

    private PhotoDateParser() {}

    /** The "date" field of the {{Information}} template (extmetadata DateTimeOriginal). */
    static Optional<PhotoDate> parseInformationDate(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String text = HTML_TAG.matcher(raw).replaceAll("");
        text = QUICK_STATEMENTS_SUFFIX.matcher(text).replaceAll("");
        text = text.replace(' ', ' ').strip();
        if (HEDGED.matcher(text).find()) {
            return Optional.empty();
        }
        text = TAKEN_PREFIX.matcher(text).replaceFirst("").strip();
        try {
            return parseStrict(text);
        } catch (DateTimeException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The camera's EXIF DateTimeOriginal. Never the EXIF "DateTime", which is when software last touched the file. */
    static Optional<LocalDate> parseExifDate(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher m = EXIF.matcher(raw.strip());
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.of(
                    Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    private static Optional<PhotoDate> parseStrict(String text) {
        Matcher m;
        if ((m = ISO_DAY.matcher(text)).matches()) {
            return Optional.of(PhotoDate.ofDay(LocalDate.of(intOf(m, 1), intOf(m, 2), intOf(m, 3))));
        }
        if ((m = ISO_MONTH.matcher(text)).matches()) {
            return Optional.of(PhotoDate.ofMonth(YearMonth.of(intOf(m, 1), intOf(m, 2))));
        }
        if ((m = YEAR.matcher(text)).matches()) {
            return Optional.of(PhotoDate.ofYear(intOf(m, 1)));
        }
        if ((m = DAY_MONTH_YEAR.matcher(text)).matches()) {
            return Optional.of(PhotoDate.ofDay(LocalDate.of(intOf(m, 3), month(m.group(2)), intOf(m, 1))));
        }
        if ((m = MONTH_DAY_YEAR.matcher(text)).matches()) {
            return Optional.of(PhotoDate.ofDay(LocalDate.of(intOf(m, 3), month(m.group(1)), intOf(m, 2))));
        }
        if ((m = MONTH_YEAR.matcher(text)).matches()) {
            return Optional.of(PhotoDate.ofMonth(YearMonth.of(intOf(m, 2), month(m.group(1)))));
        }
        return Optional.empty();
    }

    private static int intOf(Matcher m, int group) {
        return Integer.parseInt(m.group(group));
    }

    /** English month names only: Commons renders the template in English for API calls without uselang. */
    private static Month month(String name) {
        return Month.valueOf(name.toUpperCase(Locale.ROOT));
    }
}
