package app.ageguessr.features.game;

import app.ageguessr.shared.i18n.Language;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

/** A day with a round to play, for the unit tests of the game. */
public final class GameFixtures {

    public static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    public static final int AGE = 40;
    /** 2026 − 40. */
    public static final int HINT_YEAR = 1986;
    public static final String QID = "Q42";

    /** Three categories to pick, as in production. */
    public static final HintProperties HINT_PROPERTIES = new HintProperties(3);

    private GameFixtures() {}

    public static RoundViews views(HintRepository hints) {
        return new RoundViews(new HintPicker(hints), HINT_PROPERTIES);
    }

    /** Several categories of {@code count} hints each, in one eligible list. */
    public static List<Hint> hints(int count, HintCategory... categories) {
        return Arrays.stream(categories).flatMap(category -> hints(category, count).stream()).toList();
    }

    public static GameRound playableRound(short position) {
        PersonPhoto photo = PersonPhoto.builder()
                .id(UUID.randomUUID())
                .ageAtPhoto(AGE)
                .publicId(UUID.randomUUID())
                .licenseShortName("CC BY-SA 4.0")
                .sourceUrl("https://commons.wikimedia.org/wiki/File:Jane.jpg")
                .build();
        Person person = Person.builder().wikidataId(QID).nameEn("Jane Doe").build();
        return new GameRound(TODAY, position, DailyRoundStatus.PLAYABLE, photo, person);
    }

    public static GameRound neutralizedRound(short position) {
        return new GameRound(TODAY, position, DailyRoundStatus.NEUTRALIZED, null, null);
    }

    public static List<Hint> hints(HintCategory category, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> Hint.builder()
                        .id(UUID.randomUUID())
                        .category(category)
                        .year(HINT_YEAR)
                        .audience(HintAudience.ALL)
                        .textEn("Hint " + i)
                        .textFr("Indice " + i)
                        .sourceUrl("https://en.wikipedia.org/wiki/Hint")
                        .reviewStatus(HintReviewStatus.APPROVED)
                        .build())
                .toList();
    }

    public static GamePlay started(PlayerKey player, short position, int guess) {
        return GamePlay.start(TODAY, position, player, Language.EN, guess, AGE, NOW);
    }
}
