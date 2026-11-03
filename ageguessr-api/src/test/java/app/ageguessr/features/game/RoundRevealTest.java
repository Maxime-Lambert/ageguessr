package app.ageguessr.features.game;

import app.ageguessr.shared.i18n.Language;
import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoundRevealTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");
    private static final GameHint HINT = new GameHint(HintCategory.MUSIC, "A hit.", 1986, "https://en.wikipedia.org/wiki/Hit");

    @Test
    void revealsTheAnswerThePointsOfBothGuessesWhoItWasAndTheCredit() {
        GamePlay play = play(Language.EN, 30);
        play.revealHint(HintCategory.MUSIC, UUID.randomUUID());
        play.finishWithSecondGuess(38, 40, NOW);

        RoundReveal reveal = RoundReveal.of(play, photo("Jane Photographer"), person("Jane Doe", "Jeanne Doe"), HINT);

        assertThat(reveal.age()).isEqualTo(40);
        assertThat(reveal.firstGuess()).isEqualTo(30);
        assertThat(reveal.firstPoints()).isEqualTo(184);
        assertThat(reveal.secondGuess()).isEqualTo(38);
        assertThat(reveal.secondPoints()).isEqualTo(409);
        assertThat(reveal.points()).isEqualTo(184 + 409);
        assertThat(reveal.person()).isEqualTo(new RoundReveal.Identity(
                "Jane Doe", "https://www.wikidata.org/wiki/Special:GoToLinkedPage/enwiki/Q42"));
        assertThat(reveal.credit()).isEqualTo(new RoundReveal.Credit(
                "Jane Photographer",
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0",
                "https://commons.wikimedia.org/wiki/File:Jane.jpg"));
        assertThat(reveal.hint()).isEqualTo(HINT);
    }

    @Test
    void revealsAnExactFirstGuessWithoutSecondGuessNorHint() {
        RoundReveal reveal = RoundReveal.of(play(Language.EN, 40), photo(null), person("Jane Doe", null), null);

        assertThat(reveal.points()).isEqualTo(1000);
        assertThat(reveal.firstPoints()).isEqualTo(500);
        assertThat(reveal.secondGuess()).isNull();
        assertThat(reveal.secondPoints()).isNull();
        assertThat(reveal.hint()).isNull();
        assertThat(reveal.credit().author()).as("the screen says the author is unknown").isNull();
    }

    @Test
    void namesThePersonAndLinksTheArticleInFrenchForAFrenchRound() {
        RoundReveal reveal = RoundReveal.of(play(Language.FR, 40), photo(null), person("Jane Doe", "Jeanne Doe"), null);

        assertThat(reveal.person()).isEqualTo(new RoundReveal.Identity(
                "Jeanne Doe", "https://www.wikidata.org/wiki/Special:GoToLinkedPage/frwiki/Q42"));
    }

    @Test
    void fallsBackOnTheOtherNameWhenTheLanguageHasNone() {
        assertThat(RoundReveal.of(play(Language.FR, 40), photo(null), person("Jane Doe", null), null)
                        .person()
                        .name())
                .isEqualTo("Jane Doe");
        assertThat(RoundReveal.of(play(Language.EN, 40), photo(null), person(null, "Jeanne Doe"), null)
                        .person()
                        .name())
                .isEqualTo("Jeanne Doe");
    }

    private static GamePlay play(Language language, int firstGuess) {
        return GamePlay.start(DAY, (short) 1, PlayerKey.guest(UUID.randomUUID()), language, firstGuess, 40, NOW);
    }

    private static PersonPhoto photo(String author) {
        return PersonPhoto.builder()
                .ageAtPhoto(40)
                .authorText(author)
                .licenseShortName("CC BY-SA 4.0")
                .licenseUrl("https://creativecommons.org/licenses/by-sa/4.0")
                .sourceUrl("https://commons.wikimedia.org/wiki/File:Jane.jpg")
                .build();
    }

    private static Person person(String nameEn, String nameFr) {
        return Person.builder().wikidataId("Q42").nameEn(nameEn).nameFr(nameFr).build();
    }
}
