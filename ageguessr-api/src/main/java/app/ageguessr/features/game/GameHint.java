package app.ageguessr.features.game;

import app.ageguessr.shared.i18n.Language;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintCategory;

/**
 * A hint as the player sees it: its text in the round's language, then, on the result,
 * its year and its source, for the attribution: Wikidata (CC0), or Wikipedia (CC BY-SA) for
 * the hints shown before the extraction by fame (V22).
 */
public record GameHint(HintCategory category, String text, int year, String sourceUrl) {

    /** An eligible hint always has the text of the language: its audience is ALL or that one. */
    public static GameHint of(Hint hint, Language language) {
        String text = language == Language.FR ? hint.getTextFr() : hint.getTextEn();
        return new GameHint(hint.getCategory(), text, hint.getYear(), hint.getSourceUrl());
    }
}
