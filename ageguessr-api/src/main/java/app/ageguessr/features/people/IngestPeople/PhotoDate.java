package app.ageguessr.features.people.IngestPeople;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When a photo was taken, as the interval [from, to] its declared precision allows: a
 * date known to the month covers that whole month.
 */
public record PhotoDate(LocalDate from, LocalDate to) {

    // Wikibase time values: "+1976-07-14T00:00:00Z" from the APIs, without the sign from
    // SPARQL. Month and day are "00" when the precision does not reach them.
    private static final Pattern WIKIBASE_TIME = Pattern.compile("^\\+?(\\d{4})-(\\d{2})-(\\d{2})T");

    static final int PRECISION_YEAR = 9;
    static final int PRECISION_MONTH = 10;
    static final int PRECISION_DAY = 11;

    public PhotoDate {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from " + from + " is after to " + to);
        }
    }

    public static PhotoDate ofDay(LocalDate day) {
        return new PhotoDate(day, day);
    }

    public static PhotoDate ofMonth(YearMonth month) {
        return new PhotoDate(month.atDay(1), month.atEndOfMonth());
    }

    public static PhotoDate ofYear(int year) {
        return new PhotoDate(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));
    }

    /**
     * Empty for a precision coarser than the year (decade, century...), a negative year
     * or an impossible date.
     */
    public static Optional<PhotoDate> fromWikibase(String time, int precision) {
        Matcher m = WIKIBASE_TIME.matcher(time);
        if (!m.find()) {
            return Optional.empty();
        }
        int year = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        int day = Integer.parseInt(m.group(3));
        try {
            return switch (precision) {
                case PRECISION_DAY -> Optional.of(ofDay(LocalDate.of(year, month, day)));
                case PRECISION_MONTH -> Optional.of(ofMonth(YearMonth.of(year, month)));
                case PRECISION_YEAR -> Optional.of(ofYear(year));
                default -> Optional.empty();
            };
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }

    /** Empty when the two intervals do not overlap, i.e. the two sources contradict each other. */
    public Optional<PhotoDate> intersection(PhotoDate other) {
        LocalDate start = from.isAfter(other.from) ? from : other.from;
        LocalDate end = to.isBefore(other.to) ? to : other.to;
        return start.isAfter(end) ? Optional.empty() : Optional.of(new PhotoDate(start, end));
    }
}
