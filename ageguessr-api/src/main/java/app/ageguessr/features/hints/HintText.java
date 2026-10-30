package app.ageguessr.features.hints;

import java.util.regex.Pattern;

/** What holds for the text of a hint, whether it comes from a file or from an admin. */
public final class HintText {

    /** Matches the columns of V11. */
    public static final int MAX_LENGTH = 512;

    /** Any year, not just the hint's: another one would mislead the player's subtraction. */
    private static final Pattern YEAR_LIKE = Pattern.compile("(?<!\\d)(19|20)\\d{2}(?!\\d)");

    private HintText() {}

    public static boolean containsAYear(String text) {
        return text != null && YEAR_LIKE.matcher(text).find();
    }
}
