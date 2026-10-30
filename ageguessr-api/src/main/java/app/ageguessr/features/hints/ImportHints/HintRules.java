package app.ageguessr.features.hints.ImportHints;

import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintText;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What a hint must respect to enter the database. Most hints are never read by a human,
 * so these checks are what stands between an extraction bug and a player. The limits
 * match the columns of V11, which stay as the safety net.
 */
final class HintRules {

    static final int MIN_YEAR = 1900;
    static final int MAX_KEY_LENGTH = 255;
    static final int MAX_SOURCE_LENGTH = 1024;
    static final int MAX_SOURCE_KIND_LENGTH = 32;

    /** Same pattern as WikidataId, which the people feature keeps to itself. */
    private static final Pattern QID = Pattern.compile("^Q[1-9][0-9]{0,11}$");

    private static final Set<String> CATEGORIES = names(HintCategory.values());
    private static final Set<String> AUDIENCES = names(HintAudience.values());

    private HintRules() {}

    /** The reason to refuse this line, if any. The key is checked first: it names the line in the report. */
    static Optional<HintRejection> check(HintFileEntry entry, int currentYear) {
        if (isBlank(entry.dedupeKey())) {
            return Optional.of(HintRejection.MISSING_KEY);
        }
        if (entry.dedupeKey().length() > MAX_KEY_LENGTH) {
            return Optional.of(HintRejection.KEY_TOO_LONG);
        }
        if (entry.category() == null || !CATEGORIES.contains(entry.category())) {
            return Optional.of(HintRejection.UNKNOWN_CATEGORY);
        }
        if (entry.audience() == null || !AUDIENCES.contains(entry.audience())) {
            return Optional.of(HintRejection.UNKNOWN_AUDIENCE);
        }
        if (entry.year() == null || entry.year() < MIN_YEAR || entry.year() > currentYear) {
            return Optional.of(HintRejection.YEAR_OUT_OF_RANGE);
        }
        if (!textsMatchAudience(entry)) {
            return Optional.of(HintRejection.TEXTS_DO_NOT_MATCH_AUDIENCE);
        }
        if (tooLong(entry.textFr()) || tooLong(entry.textEn())) {
            return Optional.of(HintRejection.TEXT_TOO_LONG);
        }
        if (HintText.containsAYear(entry.textFr()) || HintText.containsAYear(entry.textEn())) {
            return Optional.of(HintRejection.YEAR_IN_TEXT);
        }
        if (!validSource(entry)) {
            return Optional.of(HintRejection.INVALID_SOURCE);
        }
        if (!validSubject(entry)) {
            return Optional.of(HintRejection.INVALID_SUBJECT);
        }
        return Optional.empty();
    }

    private static boolean textsMatchAudience(HintFileEntry entry) {
        boolean french = !isBlank(entry.textFr());
        boolean english = !isBlank(entry.textEn());
        return switch (HintAudience.valueOf(entry.audience())) {
            case ALL -> french && english;
            case FR -> french && entry.textEn() == null;
            case EN -> english && entry.textFr() == null;
        };
    }

    private static boolean tooLong(String text) {
        return text != null && text.length() > HintText.MAX_LENGTH;
    }

    private static boolean validSource(HintFileEntry entry) {
        return entry.sourceUrl() != null
                && entry.sourceUrl().startsWith("https://")
                && entry.sourceUrl().length() <= MAX_SOURCE_LENGTH
                && !isBlank(entry.sourceKind())
                && entry.sourceKind().length() <= MAX_SOURCE_KIND_LENGTH;
    }

    private static boolean validSubject(HintFileEntry entry) {
        if (HintCategory.CELEBRITIES.name().equals(entry.category())) {
            return entry.subjectQid() != null && QID.matcher(entry.subjectQid()).matches();
        }
        return entry.subjectQid() == null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
