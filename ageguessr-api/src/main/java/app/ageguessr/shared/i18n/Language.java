package app.ageguessr.shared.i18n;

import java.util.Locale;

/**
 * The two languages of the site (docs/decisions/architecture.md, « Internationalisation »):
 * of an account (its emails), and of a round of the game (its hint and the name revealed).
 * Shared because both auth and game use it, and features never depend on each other.
 * Stored by name, EN or FR (V13, V14).
 */
public enum Language {
    EN,
    FR;

    /** The language of an account that never chose one, and of the site by default. */
    public static final Language DEFAULT = EN;

    /** "en" or "fr": the URL prefix of the site in this language, and the Wikipedia code. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** {@link #DEFAULT} when none was chosen. */
    public static Language orDefault(Language language) {
        return language != null ? language : DEFAULT;
    }
}
