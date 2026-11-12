package app.ageguessr.features.hints;

import java.util.regex.Pattern;
import java.util.stream.IntStream;

/** What holds for the text of a hint, whether it comes from a file or from an admin. */
public final class HintText {

    /** Matches the columns of V11. */
    public static final int MAX_LENGTH = 512;

    /** Any year, not just the hint's: another one would mislead the player's subtraction. */
    private static final Pattern YEAR_LIKE = Pattern.compile("(?<!\\d)(19|20)\\d{2}(?!\\d)");

    /** One pattern per two-digit ending, compiled once: an import checks thousands of texts. */
    private static final Pattern[] TWO_DIGITS = IntStream.range(0, 100)
            .mapToObj(ending -> Pattern.compile("(?<![\\d.,])['’]?%02d(?!\\d|[.,]\\d)".formatted(ending)))
            .toArray(Pattern[]::new);

    private HintText() {}

    public static boolean containsAYear(String text) {
        return text != null && YEAR_LIKE.matcher(text).find();
    }

    /**
     * The year of the hint written in two digits gives the answer as well: "Mai 68",
     * "Windows 95", "Woodstock '69". A standalone number only: not part of a longer one,
     * nor a decimal. Only the hint's own year, a duration ("12 Years a Slave") is not one.
     */
    public static boolean containsItsYear(String text, int year) {
        return text != null && TWO_DIGITS[Math.floorMod(year, 100)].matcher(text).find();
    }
}
