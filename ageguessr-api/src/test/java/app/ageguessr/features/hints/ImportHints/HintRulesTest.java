package app.ageguessr.features.hints.ImportHints;

import static app.ageguessr.features.hints.ImportHints.HintFixtures.celebrity;
import static app.ageguessr.features.hints.ImportHints.HintFixtures.french;
import static app.ageguessr.features.hints.ImportHints.HintFixtures.withTexts;
import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.hints.HintText;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HintRulesTest {

    private static final int CURRENT_YEAR = 2026;

    private static Optional<HintRejection> check(HintFileEntry entry) {
        return HintRules.check(entry, CURRENT_YEAR);
    }

    private static HintFileEntry withYear(Integer year) {
        return new HintFileEntry(
                "MUSIC", year, "FR", "Un texte.", null, "https://fr.wikipedia.org/wiki/P", "chart", null, "key", false);
    }

    private static HintFileEntry withSource(String url, String kind) {
        return new HintFileEntry("MUSIC", 1976, "FR", "Un texte.", null, url, kind, null, "key", false);
    }

    @Test
    void acceptsAValidHint() {
        assertThat(check(french("key"))).isEmpty();
    }

    @Test
    void refusesAMissingKey() {
        assertThat(check(french(null))).contains(HintRejection.MISSING_KEY);
        assertThat(check(french(" "))).contains(HintRejection.MISSING_KEY);
    }

    @Test
    void refusesAKeyLongerThanTheColumn() {
        assertThat(check(french("k".repeat(HintRules.MAX_KEY_LENGTH)))).isEmpty();
        assertThat(check(french("k".repeat(HintRules.MAX_KEY_LENGTH + 1)))).contains(HintRejection.KEY_TOO_LONG);
    }

    @Test
    void refusesAnUnknownCategoryOrAudience() {
        HintFileEntry category = new HintFileEntry(
                "TV", 1976, "FR", "Un texte.", null, "https://fr.wikipedia.org/wiki/P", "chart", null, "key", false);
        HintFileEntry audience = withTexts("DE", "Un texte.", null);

        assertThat(check(category)).contains(HintRejection.UNKNOWN_CATEGORY);
        assertThat(check(audience)).contains(HintRejection.UNKNOWN_AUDIENCE);
    }

    @Test
    void refusesAYearOutOfRange() {
        assertThat(check(withYear(null))).contains(HintRejection.YEAR_OUT_OF_RANGE);
        assertThat(check(withYear(1899))).contains(HintRejection.YEAR_OUT_OF_RANGE);
        assertThat(check(withYear(1900))).isEmpty();
        assertThat(check(withYear(CURRENT_YEAR))).isEmpty();
        assertThat(check(withYear(CURRENT_YEAR + 1))).contains(HintRejection.YEAR_OUT_OF_RANGE);
    }

    @Test
    void anAudienceOwnsExactlyItsTexts() {
        assertThat(check(withTexts("ALL", "Un texte.", "A text."))).isEmpty();
        assertThat(check(withTexts("EN", null, "A text."))).isEmpty();
        assertThat(check(withTexts("ALL", "Un texte.", null))).contains(HintRejection.TEXTS_DO_NOT_MATCH_AUDIENCE);
        assertThat(check(withTexts("ALL", null, "A text."))).contains(HintRejection.TEXTS_DO_NOT_MATCH_AUDIENCE);
        assertThat(check(withTexts("FR", null, null))).contains(HintRejection.TEXTS_DO_NOT_MATCH_AUDIENCE);
        assertThat(check(withTexts("FR", " ", null))).contains(HintRejection.TEXTS_DO_NOT_MATCH_AUDIENCE);
        assertThat(check(withTexts("FR", "Un texte.", "A text."))).contains(HintRejection.TEXTS_DO_NOT_MATCH_AUDIENCE);
        assertThat(check(withTexts("EN", "Un texte.", "A text."))).contains(HintRejection.TEXTS_DO_NOT_MATCH_AUDIENCE);
    }

    @Test
    void refusesATextLongerThanTheColumn() {
        String longest = "a".repeat(HintText.MAX_LENGTH);

        assertThat(check(withTexts("FR", longest, null))).isEmpty();
        assertThat(check(withTexts("FR", longest + "a", null))).contains(HintRejection.TEXT_TOO_LONG);
        assertThat(check(withTexts("EN", null, longest + "a"))).contains(HintRejection.TEXT_TOO_LONG);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sorti en 1976.", "« 1999 », de Prince.", "2099", "(1900)", "Top 2015 !"})
    void refusesATextContainingAYear(String text) {
        assertThat(check(withTexts("FR", text, null))).contains(HintRejection.YEAR_IN_TEXT);
        assertThat(check(withTexts("EN", null, text))).contains(HintRejection.YEAR_IN_TEXT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"In the Year 2525", "Boeing 1899", "Numéro 2100", "Code 19991", "Vol 12015", "Top 50"})
    void acceptsANumberThatCannotBeAYear(String text) {
        assertThat(check(withTexts("FR", text, null))).isEmpty();
    }

    @Test
    void aSourceNeedsAnHttpsUrlAndAKind() {
        assertThat(check(withSource(null, "chart"))).contains(HintRejection.INVALID_SOURCE);
        assertThat(check(withSource("http://fr.wikipedia.org/wiki/P", "chart"))).contains(HintRejection.INVALID_SOURCE);
        assertThat(check(withSource("javascript:alert(1)", "chart"))).contains(HintRejection.INVALID_SOURCE);
        assertThat(check(withSource("https://fr.wikipedia.org/wiki/P", null))).contains(HintRejection.INVALID_SOURCE);
        assertThat(check(withSource("https://fr.wikipedia.org/wiki/P", "k".repeat(33))))
                .contains(HintRejection.INVALID_SOURCE);
        assertThat(check(withSource("https://x.org/" + "a".repeat(1024), "chart")))
                .contains(HintRejection.INVALID_SOURCE);
    }

    @Test
    void aCelebrityNamesItsSubjectAndNoOtherHintDoes() {
        HintFileEntry musicWithSubject = new HintFileEntry(
                "MUSIC", 1976, "FR", "Un texte.", null, "https://fr.wikipedia.org/wiki/P", "chart", "Q42", "key", false);

        assertThat(check(celebrity("Q42"))).isEmpty();
        assertThat(check(celebrity(null))).contains(HintRejection.INVALID_SUBJECT);
        assertThat(check(celebrity("42"))).contains(HintRejection.INVALID_SUBJECT);
        assertThat(check(musicWithSubject)).contains(HintRejection.INVALID_SUBJECT);
    }
}
