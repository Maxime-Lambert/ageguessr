package app.ageguessr.integration.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.RoundState;
import app.ageguessr.features.game.DailyRoundStatus;
import app.ageguessr.features.game.DrawDailyGame.DrawCandidate;
import app.ageguessr.features.game.DrawDailyGame.DrawCandidateRepository;
import app.ageguessr.features.game.DrawDailyGame.DrawDailyGameHandler;
import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.ExclusionReason;
import app.ageguessr.features.people.Gender;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.ReviewStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The daily draw against a real Postgres: V12 constraints, its ON DELETE SET NULL with the
 * exclusion trigger of V4, the candidate query and concurrent draws. Not @Transactional:
 * the handler commits in its own transactions and the concurrency test needs two threads
 * to see each other's writes, so every test cleans up the rows it committed.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DailyRoundsIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    private static final AtomicInteger QIDS = new AtomicInteger();

    @Autowired
    private DrawDailyGameHandler handler;

    @Autowired
    private DailyRoundRepository rounds;

    @Autowired
    private DrawCandidateRepository candidates;

    @Autowired
    private PersonRepository people;

    @Autowired
    private PersonPhotoRepository photos;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM daily_rounds");
        jdbc.update("DELETE FROM people");
    }

    @Test
    void drawsADayOnceAndReturnsTheSameRoundsAfterwards() {
        seedThreeBoxes();

        List<DailyRound> first = handler.drawIfMissing(DAY);
        List<DailyRound> second = handler.drawIfMissing(DAY);

        assertThat(first).hasSize(3);
        assertThat(second).extracting(DailyRound::getPhotoId)
                .containsExactlyElementsOf(first.stream().map(DailyRound::getPhotoId).toList());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_rounds", Integer.class)).isEqualTo(3);
    }

    @Test
    void renewsThePublicIdOfTheDrawnPhotos() {
        List<PersonPhoto> seeded = seedThreeBoxes();
        List<UUID> before = seeded.stream().map(PersonPhoto::getPublicId).toList();

        handler.drawIfMissing(DAY);

        assertThat(photos.findAllById(seeded.stream().map(PersonPhoto::getId).toList()))
                .extracting(PersonPhoto::getPublicId)
                .doesNotContainAnyElementsOf(before);
    }

    @Test
    void drawsOnlyOneGameWhenTwoDrawsRunAtTheSameTime() {
        seedThreeBoxes();
        CountDownLatch start = new CountDownLatch(1);

        CompletableFuture<List<DailyRound>> a = CompletableFuture.supplyAsync(() -> awaitThenDraw(start));
        CompletableFuture<List<DailyRound>> b = CompletableFuture.supplyAsync(() -> awaitThenDraw(start));
        start.countDown();

        List<UUID> photosOfA = a.join().stream().map(DailyRound::getPhotoId).toList();
        List<UUID> photosOfB = b.join().stream().map(DailyRound::getPhotoId).toList();
        assertThat(photosOfA).hasSize(3).containsExactlyElementsOf(photosOfB);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_rounds", Integer.class)).isEqualTo(3);
    }

    @Test
    void refusesTwoRoundsAtTheSamePosition() {
        PersonPhoto photo = seedThreeBoxes().getFirst();
        rounds.saveAndFlush(round(1, photo.getId()));

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO daily_rounds (game_date, position, photo_id) VALUES (?, 1, NULL)", DAY))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTheSamePhotoTwiceInADay() {
        PersonPhoto photo = seedThreeBoxes().getFirst();
        rounds.saveAndFlush(round(1, photo.getId()));

        assertThatThrownBy(() -> rounds.saveAndFlush(round(2, photo.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAPositionBelowOne() {
        assertThatThrownBy(() -> rounds.saveAndFlush(round(0, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsTheRoundWithoutItsPhotoWhenThePersonIsExcluded() {
        PersonPhoto photo = seedThreeBoxes().getFirst();
        rounds.saveAndFlush(round(1, photo.getId()));
        Person person = people.findById(photo.getPersonId()).orElseThrow();

        person.exclude(ExclusionReason.MANUAL);
        people.saveAndFlush(person);

        assertThat(photos.findById(photo.getId())).isEmpty();
        assertThat(rounds.findStatesOf(DAY))
                .containsExactly(new RoundState((short) 1, null, DailyRoundStatus.NEUTRALIZED));
    }

    @Test
    void neutralizesARoundWhosePhotoIsRejectedAndRestoresItWhenApprovedAgain() {
        PersonPhoto photo = seedThreeBoxes().getFirst();
        rounds.saveAndFlush(round(1, photo.getId()));

        setStatus(photo, ReviewStatus.REJECTED);
        assertThat(rounds.findStatesOf(DAY)).extracting(RoundState::status).containsExactly(DailyRoundStatus.NEUTRALIZED);

        setStatus(photo, ReviewStatus.APPROVED);
        assertThat(rounds.findStatesOf(DAY)).extracting(RoundState::status).containsExactly(DailyRoundStatus.PLAYABLE);
    }

    @Test
    void offersOnlyApprovedPhotosOfPeopleNotExcludedWithTheirLastDayPlayed() {
        Person approvedPerson = savePerson(Gender.FEMALE, Continent.ASIA);
        PersonPhoto approved = savePhoto(approvedPerson, 1980, ReviewStatus.APPROVED);
        PersonPhoto neverPlayed = savePhoto(approvedPerson, 1995, ReviewStatus.APPROVED);
        savePhoto(approvedPerson, 2010, ReviewStatus.PENDING);
        savePhoto(approvedPerson, 2020, ReviewStatus.REJECTED);
        Person excluded = savePerson(Gender.MALE, Continent.EUROPE);
        savePhoto(excluded, 1980, ReviewStatus.APPROVED);
        excluded.exclude(ExclusionReason.EDITORIAL_LINE);
        people.saveAndFlush(excluded);
        rounds.saveAndFlush(round(DAY.minusYears(2), 1, approved.getId()));
        rounds.saveAndFlush(round(DAY.minusYears(1), 2, approved.getId()));

        assertThat(candidates.findCandidates())
                .containsExactlyInAnyOrder(
                        new DrawCandidate(approved.getId(), Gender.FEMALE, Continent.ASIA, DAY.minusYears(1)),
                        new DrawCandidate(neverPlayed.getId(), Gender.FEMALE, Continent.ASIA, null));
    }

    private List<DailyRound> awaitThenDraw(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return handler.drawIfMissing(DAY);
    }

    /** One approved photo in each of three boxes, so that a full day can be drawn. */
    private List<PersonPhoto> seedThreeBoxes() {
        return List.of(
                savePhoto(savePerson(Gender.FEMALE, Continent.AFRICA), 1980, ReviewStatus.APPROVED),
                savePhoto(savePerson(Gender.MALE, Continent.AFRICA), 1980, ReviewStatus.APPROVED),
                savePhoto(savePerson(Gender.FEMALE, Continent.EUROPE), 1980, ReviewStatus.APPROVED));
    }

    private void setStatus(PersonPhoto photo, ReviewStatus status) {
        PersonPhoto stored = photos.findById(photo.getId()).orElseThrow();
        stored.setReviewStatus(status);
        photos.saveAndFlush(stored);
    }

    private Person savePerson(Gender gender, Continent continent) {
        return people.saveAndFlush(Person.builder()
                .wikidataId("Q" + QIDS.incrementAndGet())
                .nameEn("Jane Doe")
                .birthDate(LocalDate.of(1950, 3, 10))
                .sitelinks(42)
                .gender(gender)
                .continent(continent)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    /** A photo dated July of {@code year}: after the March 10 birthday, so the age is year - 1950. */
    private PersonPhoto savePhoto(Person person, int year, ReviewStatus status) {
        return photos.saveAndFlush(PersonPhoto.builder()
                .personId(person.getId())
                .commonsFile(year + ".jpg")
                .takenFrom(LocalDate.of(year, 7, 1))
                .takenTo(LocalDate.of(year, 7, 31))
                .ageAtPhoto(year - 1950)
                .dateSource(PhotoDateSource.BOTH)
                .licenseShortName("CC BY-SA 4.0")
                .sourceUrl("https://commons.wikimedia.org/wiki/File:" + year + ".jpg")
                .publicId(UUID.randomUUID())
                .reviewStatus(status)
                .createdAt(Instant.now())
                .build());
    }

    private static DailyRound round(int position, UUID photoId) {
        return round(DAY, position, photoId);
    }

    private static DailyRound round(LocalDate day, int position, UUID photoId) {
        return DailyRound.builder()
                .gameDate(day)
                .position((short) position)
                .photoId(photoId)
                .drawnAt(Instant.now())
                .build();
    }
}
