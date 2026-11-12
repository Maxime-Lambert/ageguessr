package app.ageguessr.integration.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.shared.i18n.Language;
import app.ageguessr.features.game.HintPicker;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The constraints and cascades of V13__create_game_plays.sql (hints as arrays since V20), and the hint query of the
 * game, against a real Postgres.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class GamePlaysIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    private static final Instant NOW = Instant.parse("2026-11-02T08:00:00Z");
    private static final AtomicInteger KEYS = new AtomicInteger();

    /** A complete, valid row; each refusal changes one column. */
    private static final String VALID_COLUMNS = "game_date, position, guest_id, language, first_guess, first_points";

    @Autowired
    private GamePlayRepository plays;

    @Autowired
    private DailyRoundRepository rounds;

    @Autowired
    private HintRepository hints;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void drawTheDay() {
        // photo_id may be null: the foreign key of game_plays is on the round, not the photo.
        for (short position = 1; position <= 2; position++) {
            rounds.saveAndFlush(DailyRound.builder()
                    .gameDate(DAY)
                    .position(position)
                    .drawnAt(NOW)
                    .build());
        }
    }

    @Test
    void savesTheRoundOfAGuestAndOfAnAccount() {
        User user = saveUser();

        plays.saveAndFlush(start(PlayerKey.guest(UUID.randomUUID()), (short) 1));
        plays.saveAndFlush(start(PlayerKey.account(user.getId()), (short) 1));

        assertThat(plays.count()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "INSERT INTO game_plays (game_date, position, language, first_guess, first_points)"
                        + " VALUES ('2026-11-02', 1, 'EN', 30, 184)",
                "INSERT INTO game_plays (" + VALID_COLUMNS + ") VALUES ('2026-11-02', 1, gen_random_uuid(), 'DE', 30, 184)",
                "INSERT INTO game_plays (" + VALID_COLUMNS + ") VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 17, 0)",
                "INSERT INTO game_plays (" + VALID_COLUMNS + ") VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 101, 0)",
                "INSERT INTO game_plays (" + VALID_COLUMNS + ") VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 30, 501)",
                "INSERT INTO game_plays (" + VALID_COLUMNS + ") VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 30, -1)",
                "INSERT INTO game_plays (" + VALID_COLUMNS + ", hint_categories, hint_ids)"
                        + " VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 30, 184, '{COOKING}', ARRAY[gen_random_uuid()])",
                // Categories and hints go together (V20).
                "INSERT INTO game_plays (" + VALID_COLUMNS + ", hint_categories)"
                        + " VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 30, 184, '{MUSIC}')",
                // Only a hint shown is reported.
                "INSERT INTO game_plays (" + VALID_COLUMNS + ", hint_categories, hint_ids, reported_hint_categories)"
                        + " VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 30, 184, '{MUSIC}', ARRAY[gen_random_uuid()],"
                        + " '{FILM}')",
                // A second guess without its points, then without the end of the round.
                "INSERT INTO game_plays (" + VALID_COLUMNS + ", second_guess, finished_at)"
                        + " VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 30, 184, 38, now())",
                "INSERT INTO game_plays (" + VALID_COLUMNS + ", second_guess, second_points)"
                        + " VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 30, 184, 38, 409)",
                "INSERT INTO game_plays (" + VALID_COLUMNS + ", second_guess, second_points, finished_at)"
                        + " VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 30, 184, 101, 0, now())",
                // A round that was never drawn.
                "INSERT INTO game_plays (" + VALID_COLUMNS + ") VALUES ('2026-11-02', 3, gen_random_uuid(), 'EN', 30, 184)",
            })
    void theDatabaseRefuses(String insert) {
        assertThatThrownBy(() -> jdbc.update(insert)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesARowOfBothAnAccountAndAGuest() {
        UUID userId = saveUser().getId();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO game_plays (" + VALID_COLUMNS + ", user_id)"
                                + " VALUES ('2026-11-02', 1, gen_random_uuid(), 'EN', 30, 184, ?)",
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("game_plays_one_player");
    }

    @Test
    void refusesASecondRowForTheSameGuestAndRound() {
        PlayerKey guest = PlayerKey.guest(UUID.randomUUID());
        plays.saveAndFlush(start(guest, (short) 1));

        assertThatThrownBy(() -> plays.saveAndFlush(start(guest, (short) 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void keepsTheHintsOfARoundAndTheirReportsInTheirOrder() {
        GamePlay play = start(PlayerKey.guest(UUID.randomUUID()), (short) 1);
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        play.revealHints(List.of(HintCategory.MUSIC, HintCategory.SPORT, HintCategory.CELEBRITIES), ids);
        play.finishWithSecondGuess(38, 40, NOW);
        play.progress().reportHint(HintCategory.SPORT);
        plays.saveAndFlush(play);
        entityManager.clear();

        GamePlay read = plays.findById(play.getId()).orElseThrow();
        assertThat(read.getHintCategories()).containsExactly(HintCategory.MUSIC, HintCategory.SPORT, HintCategory.CELEBRITIES);
        assertThat(read.getHintIds()).containsExactlyElementsOf(ids);
        assertThat(read.getReportedHintCategories()).containsExactly(HintCategory.SPORT);

        // A report after reading it back is saved too: the dirty check sees the new list.
        read.progress().reportHint(HintCategory.MUSIC);
        plays.saveAndFlush(read);
        entityManager.clear();
        assertThat(plays.findById(play.getId()).orElseThrow().getReportedHintCategories())
                .containsExactly(HintCategory.SPORT, HintCategory.MUSIC);
    }

    @Test
    void refusesASecondRowForTheSameAccountAndRound() {
        PlayerKey account = PlayerKey.account(saveUser().getId());
        plays.saveAndFlush(start(account, (short) 1));

        assertThatThrownBy(() -> plays.saveAndFlush(start(account, (short) 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void letsManyPlayersPlayTheSameRoundAndOnePlayerEveryRound() {
        PlayerKey guest = PlayerKey.guest(UUID.randomUUID());

        plays.saveAndFlush(start(guest, (short) 1));
        plays.saveAndFlush(start(guest, (short) 2));
        plays.saveAndFlush(start(PlayerKey.guest(UUID.randomUUID()), (short) 1));
        plays.saveAndFlush(start(PlayerKey.account(saveUser().getId()), (short) 1));
        plays.saveAndFlush(start(PlayerKey.account(saveUser().getId()), (short) 1));

        assertThat(plays.count()).isEqualTo(5);
    }

    @Test
    void readsTheRoundsOfOnePlayerOnly() {
        PlayerKey guest = PlayerKey.guest(UUID.randomUUID());
        PlayerKey account = PlayerKey.account(saveUser().getId());
        plays.saveAndFlush(start(guest, (short) 1));
        plays.saveAndFlush(start(guest, (short) 2));
        plays.saveAndFlush(start(account, (short) 1));
        plays.saveAndFlush(start(PlayerKey.guest(UUID.randomUUID()), (short) 1));

        assertThat(plays.findOf(guest, DAY)).extracting(GamePlay::getPosition).containsExactlyInAnyOrder((short) 1, (short) 2);
        assertThat(plays.findOf(account, DAY)).extracting(GamePlay::getUserId).containsExactly(account.userId());
        assertThat(plays.findOf(guest, DAY.minusDays(1))).isEmpty();
    }

    @Test
    void deletingTheAccountDeletesItsRounds() {
        User user = saveUser();
        plays.saveAndFlush(start(PlayerKey.account(user.getId()), (short) 1));
        plays.saveAndFlush(start(PlayerKey.guest(UUID.randomUUID()), (short) 1));

        users.delete(user);
        users.flush();
        entityManager.clear();

        assertThat(plays.findAll()).extracting(GamePlay::getUserId).containsOnlyNulls().hasSize(1);
    }

    @Test
    void deletingAHintKeepsTheRoundWhichNoLongerShowsIt() {
        Hint hint = saveHint(HintCategory.MUSIC, 1986, HintAudience.ALL, HintReviewStatus.APPROVED, null);
        GamePlay play = start(PlayerKey.guest(UUID.randomUUID()), (short) 1);
        play.revealHints(List.of(HintCategory.MUSIC), List.of(hint.getId()));
        plays.saveAndFlush(play);

        // No foreign key on an array (V20): the id stays, and the game skips an unknown one.
        hints.delete(hint);
        hints.flush();
        entityManager.clear();

        GamePlay reloaded = plays.findById(play.getId()).orElseThrow();
        assertThat(reloaded.getHintCategories()).containsExactly(HintCategory.MUSIC);
        assertThat(new HintPicker(hints).shown(reloaded.getHintIds(), Language.EN)).isEmpty();
    }

    @Test
    void offersTheApprovedHintsOfTheYearForTheLanguageWithoutThePersonToGuess() {
        Hint all = saveHint(HintCategory.MUSIC, 1986, HintAudience.ALL, HintReviewStatus.APPROVED, null);
        Hint french = saveHint(HintCategory.FILM, 1986, HintAudience.FR, HintReviewStatus.APPROVED, null);
        Hint otherCelebrity = saveHint(HintCategory.CELEBRITIES, 1986, HintAudience.ALL, HintReviewStatus.APPROVED, "Q1");
        saveHint(HintCategory.CELEBRITIES, 1986, HintAudience.ALL, HintReviewStatus.APPROVED, "Q42");
        saveHint(HintCategory.MUSIC, 1986, HintAudience.EN, HintReviewStatus.APPROVED, null);
        saveHint(HintCategory.MUSIC, 1986, HintAudience.ALL, HintReviewStatus.PENDING, null);
        saveHint(HintCategory.MUSIC, 1986, HintAudience.ALL, HintReviewStatus.REJECTED, null);
        saveHint(HintCategory.MUSIC, 1985, HintAudience.ALL, HintReviewStatus.APPROVED, null);

        List<Hint> eligible = hints.findEligible(1986, HintPicker.audiences(Language.FR), "Q42");

        assertThat(eligible)
                .extracting(Hint::getId)
                .containsExactlyElementsOf(List.of(all, french, otherCelebrity).stream()
                        .map(Hint::getId)
                        // Postgres sorts a uuid byte by byte, like its text; UUID.compareTo
                        // compares signed longs and may not agree.
                        .sorted(Comparator.comparing(UUID::toString))
                        .toList());
    }

    private static GamePlay start(PlayerKey player, short position) {
        return GamePlay.start(DAY, position, player, Language.EN, 30, 40, NOW);
    }

    private User saveUser() {
        int key = KEYS.incrementAndGet();
        return users.saveAndFlush(User.builder()
                .email("player" + key + "@x.com")
                .username("player" + key)
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build());
    }

    private Hint saveHint(HintCategory category, int year, HintAudience audience, HintReviewStatus status, String subjectQid) {
        return hints.saveAndFlush(Hint.builder()
                .category(category)
                .year(year)
                .audience(audience)
                .textEn(audience == HintAudience.FR ? null : "A text.")
                .textFr(audience == HintAudience.EN ? null : "Un texte.")
                .sourceUrl("https://en.wikipedia.org/wiki/Page")
                .sourceKind("chart")
                .subjectQid(subjectQid)
                .dedupeKey("key-" + KEYS.incrementAndGet())
                .reviewStatus(status)
                .createdAt(Instant.now())
                .build());
    }
}
