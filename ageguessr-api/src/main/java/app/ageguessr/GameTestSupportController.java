package app.ageguessr;

import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.PhotoImage;
import app.ageguessr.features.people.PhotoImageRepository;
import app.ageguessr.features.people.ReviewStatus;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only: gives the Playwright journeys a known game today, without the random draw.
 * Replaces today's rounds (and the games played on them) with three new people, one per
 * box, each with an approved photo, an image and enough hints in two categories. Returns
 * the ages, so a journey can play an exact guess.
 *
 * <p>Gated by @Profile("e2e") like TestSupportController - MUST NEVER be active in the
 * prod deployment config - and only reachable under that profile (TestSupportSecurityConfig).
 */
@RestController
@Profile("e2e")
@RequiredArgsConstructor
public class GameTestSupportController {

    /** One person per position, in different boxes as the draw would pick them. */
    private static final List<Seed> SEEDS = List.of(
            new Seed(Gender.FEMALE, Continent.EUROPE, 40),
            new Seed(Gender.MALE, Continent.ASIA, 55),
            new Seed(Gender.FEMALE, Continent.AFRICA, 30));

    private static final List<HintCategory> CATEGORIES = List.of(HintCategory.MUSIC, HintCategory.FILM);

    private final PersonRepository people;
    private final PersonPhotoRepository photos;
    private final PhotoImageRepository images;
    private final HintRepository hints;
    private final DailyRoundRepository rounds;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @PostMapping("/test-support/seed-daily-game")
    @Transactional
    public SeededGame seedDailyGame() {
        LocalDate today = LocalDate.now(clock);
        jdbc.update("DELETE FROM game_plays WHERE game_date = ?", today);
        jdbc.update("DELETE FROM daily_rounds WHERE game_date = ?", today);
        byte[] jpeg = jpeg();
        for (int i = 0; i < SEEDS.size(); i++) {
            Seed seed = SEEDS.get(i);
            PersonPhoto photo = savePhoto(savePerson(seed, i + 1), seed.age(), today, jpeg);
            saveHints(today.getYear() - seed.age());
            rounds.save(DailyRound.builder()
                    .gameDate(today)
                    .position((short) (i + 1))
                    .photoId(photo.getId())
                    .drawnAt(Instant.now(clock))
                    .build());
        }
        return new SeededGame(today, SEEDS.stream().map(Seed::age).toList());
    }

    public record SeededGame(LocalDate date, List<Integer> ages) {}

    private record Seed(Gender gender, Continent continent, int age) {}

    private Person savePerson(Seed seed, int position) {
        return people.save(Person.builder()
                .wikidataId("Q9" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000_000L))
                .nameEn("E2E Person " + position)
                .nameFr("Personne E2E " + position)
                .birthDate(LocalDate.of(1950, 1, 1))
                .sitelinks(100)
                .gender(seed.gender())
                .continent(seed.continent())
                .createdAt(Instant.now(clock))
                .updatedAt(Instant.now(clock))
                .build());
    }

    private PersonPhoto savePhoto(Person person, int age, LocalDate today, byte[] jpeg) {
        int year = 1950 + age;
        PersonPhoto photo = photos.save(PersonPhoto.builder()
                .personId(person.getId())
                .commonsFile("E2E_" + UUID.randomUUID() + ".jpg")
                .takenFrom(LocalDate.of(year, 6, 1))
                .takenTo(LocalDate.of(year, 6, 30))
                .ageAtPhoto(age)
                .dateSource(PhotoDateSource.BOTH)
                .licenseShortName("CC BY-SA 4.0")
                .licenseUrl("https://creativecommons.org/licenses/by-sa/4.0")
                .authorText("E2E Photographer")
                .sourceUrl("https://commons.wikimedia.org/wiki/File:E2E.jpg")
                .publicId(UUID.randomUUID())
                .reviewStatus(ReviewStatus.APPROVED)
                .createdAt(Instant.now(clock))
                .build());
        images.save(PhotoImage.builder()
                .photoId(photo.getId())
                .content(jpeg)
                .contentType("image/jpeg")
                .width(8)
                .height(8)
                .build());
        return photo;
    }

    /** Exactly the minimum per category, so that both are offered. */
    private void saveHints(int year) {
        for (HintCategory category : CATEGORIES) {
            for (int i = 0; i < Hint.MINIMUM_PER_CATEGORY; i++) {
                hints.save(Hint.builder()
                        .category(category)
                        .year(year)
                        .audience(HintAudience.ALL)
                        .textEn("E2E %s hint %d".formatted(category, i))
                        .textFr("Indice E2E %s %d".formatted(category, i))
                        .sourceUrl("https://en.wikipedia.org/wiki/E2E")
                        .sourceKind("e2e")
                        .dedupeKey("e2e-" + UUID.randomUUID())
                        .reviewStatus(HintReviewStatus.APPROVED)
                        .createdAt(Instant.now(clock))
                        .build());
            }
        }
    }

    /** A real, decodable JPEG: the browser must load it, or the round looks removed. */
    private static byte[] jpeg() {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
