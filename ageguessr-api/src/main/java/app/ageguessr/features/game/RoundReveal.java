package app.ageguessr.features.game;

import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import java.util.Locale;

/**
 * What a finished round reveals: the answer, the points, who it was and the photo's
 * credit. Built only once the round is finished; before that, nothing of it leaves the
 * API. The points of the first guess appear here for the first time.
 *
 * @param secondGuess null after an exact first guess, as {@code secondPoints}
 * @param hint null without a hint, or when it was rejected since
 */
public record RoundReveal(
        int age,
        int firstGuess,
        int firstPoints,
        Integer secondGuess,
        Integer secondPoints,
        int points,
        Identity person,
        Credit credit,
        GameHint hint) {

    /**
     * The Wikipedia article in the player's language, without storing its title: Wikidata
     * redirects to it. Checked on 2026-11-02: a 302 to the article when it exists; without
     * one, Wikidata shows its own page (200). Rare for people picked by sitelinks.
     */
    static final String WIKIPEDIA_LINK = "https://www.wikidata.org/wiki/Special:GoToLinkedPage/%swiki/%s";

    /** @param wikipediaUrl the article in the round's language */
    public record Identity(String name, String wikipediaUrl) {}

    /** @param author null when Commons names none, the screen then says so */
    public record Credit(String author, String license, String licenseUrl, String sourceUrl) {}

    public static RoundReveal of(GamePlay play, PersonPhoto photo, Person person, GameHint hint) {
        Language language = play.getLanguage();
        return new RoundReveal(
                photo.getAgeAtPhoto(),
                play.getFirstGuess(),
                play.getFirstPoints(),
                play.getSecondGuess(),
                play.getSecondPoints(),
                play.points(),
                new Identity(name(person, language), WIKIPEDIA_LINK.formatted(code(language), person.getWikidataId())),
                new Credit(photo.getAuthorText(), photo.getLicenseShortName(), photo.getLicenseUrl(), photo.getSourceUrl()),
                hint);
    }

    /** In the round's language, else the other one: ingestion keeps a person with either. */
    private static String name(Person person, Language language) {
        String preferred = language == Language.FR ? person.getNameFr() : person.getNameEn();
        String other = language == Language.FR ? person.getNameEn() : person.getNameFr();
        return preferred != null ? preferred : other;
    }

    private static String code(Language language) {
        return language.name().toLowerCase(Locale.ROOT);
    }
}
