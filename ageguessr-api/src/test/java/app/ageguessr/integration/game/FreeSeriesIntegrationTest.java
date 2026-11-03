package app.ageguessr.integration.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.game.FreeSeries;
import app.ageguessr.features.game.FreeSeriesPlay;
import app.ageguessr.features.game.FreeSeriesPlayRepository;
import app.ageguessr.features.game.FreeSeriesPlayer;
import app.ageguessr.features.game.FreeSeriesPlayerRepository;
import app.ageguessr.features.game.FreeSeriesRepository;
import app.ageguessr.features.game.FreeSeriesRound;
import app.ageguessr.features.game.FreeSeriesRoundRepository;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.shared.i18n.Language;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** The constraints and cascades of V15__create_free_series.sql, against a real Postgres. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FreeSeriesIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 3);
    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
    private static final AtomicInteger KEYS = new AtomicInteger();

    @Autowired
    private FreeSeriesRepository series;

    @Autowired
    private FreeSeriesRoundRepository rounds;

    @Autowired
    private FreeSeriesPlayerRepository players;

    @Autowired
    private FreeSeriesPlayRepository plays;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void keepsASeriesItsRoundsItsPlayersAndTheirPlays() {
        PlayerKey guest = PlayerKey.guest(UUID.randomUUID());
        FreeSeries saved = seriesOf(guest);
        players.saveAndFlush(FreeSeriesPlayer.of(saved.getId(), guest, null, NOW));
        plays.saveAndFlush(FreeSeriesPlay.start(saved.getId(), (short) 1, guest, Language.FR, 30, 40, NOW));
        entityManager.clear();

        FreeSeriesPlay play = plays.findOf(guest, saved.getId(), (short) 1).orElseThrow();
        assertThat(play.getLanguage()).isEqualTo(Language.FR);
        assertThat(play.getFirstPoints()).isPositive();
        assertThat(players.findOf(saved.getId(), guest)).isPresent();
        assertThat(series.findLatestOf(guest)).map(FreeSeries::getId).contains(saved.getId());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                // Neither an account nor a guest, then both.
                "INSERT INTO free_series (size, created_on) VALUES (5, '2026-11-03')",
                "INSERT INTO free_series (size, created_on, guest_id, user_id)"
                        + " VALUES (5, '2026-11-03', gen_random_uuid(), gen_random_uuid())",
                "INSERT INTO free_series (size, created_on, guest_id) VALUES (7, '2026-11-03', gen_random_uuid())",
                // A code without its date, then a code outside base32 Crockford (I, L, O, U).
                "INSERT INTO free_series (size, created_on, guest_id, share_code)"
                        + " VALUES (5, '2026-11-03', gen_random_uuid(), 'K7F2QX9M')",
                "INSERT INTO free_series (size, created_on, guest_id, share_code, shared_at)"
                        + " VALUES (5, '2026-11-03', gen_random_uuid(), 'K7F2QXIL', now())",
                "INSERT INTO free_series (size, created_on, guest_id, share_code, shared_at)"
                        + " VALUES (5, '2026-11-03', gen_random_uuid(), 'k7f2qx9m', now())"
            })
    void refusesAnInvalidSeries(String insert) {
        assertThatThrownBy(() -> jdbc.update(insert)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesTwoSeriesWithTheSameCode() {
        String insert = "INSERT INTO free_series (size, created_on, guest_id, share_code, shared_at)"
                + " VALUES (5, '2026-11-03', gen_random_uuid(), 'K7F2QX9M', now())";
        jdbc.update(insert);

        assertThatThrownBy(() -> jdbc.update(insert)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesANicknameForAnAccount() {
        UUID seriesId = seriesOf(PlayerKey.guest(UUID.randomUUID())).getId();
        UUID userId = saveUser().getId();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO free_series_players (series_id, user_id, nickname) VALUES (?, ?, 'Nick')",
                        seriesId,
                        userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAnInvalidNickname() {
        UUID seriesId = seriesOf(PlayerKey.guest(UUID.randomUUID())).getId();

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO free_series_players (series_id, guest_id, nickname) VALUES (?, gen_random_uuid(), 'a b')",
                        seriesId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void letsAPlayerJoinASeriesOnlyOnce() {
        PlayerKey guest = PlayerKey.guest(UUID.randomUUID());
        UUID seriesId = seriesOf(PlayerKey.guest(UUID.randomUUID())).getId();
        players.saveAndFlush(FreeSeriesPlayer.of(seriesId, guest, "Nick", NOW));

        assertThatThrownBy(() -> players.saveAndFlush(FreeSeriesPlayer.of(seriesId, guest, "Other", NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesASecondPlayOfTheSameRound() {
        PlayerKey guest = PlayerKey.guest(UUID.randomUUID());
        UUID seriesId = seriesOf(guest).getId();
        plays.saveAndFlush(FreeSeriesPlay.start(seriesId, (short) 1, guest, Language.EN, 30, 40, NOW));

        assertThatThrownBy(() -> plays.saveAndFlush(
                        FreeSeriesPlay.start(seriesId, (short) 1, guest, Language.EN, 31, 40, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAnAccountDeletesItsSeriesAndItsPlacesInTheOthers() {
        User user = saveUser();
        PlayerKey account = PlayerKey.account(user.getId());
        UUID own = seriesOf(account).getId();
        UUID other = seriesOf(PlayerKey.guest(UUID.randomUUID())).getId();
        players.saveAndFlush(FreeSeriesPlayer.of(other, account, null, NOW));
        plays.saveAndFlush(FreeSeriesPlay.start(other, (short) 1, account, Language.EN, 30, 40, NOW));

        users.delete(user);
        users.flush();
        entityManager.clear();

        assertThat(series.findById(own)).isEmpty();
        assertThat(series.findById(other)).isPresent();
        assertThat(players.findBySeriesId(other)).isEmpty();
        assertThat(plays.findBySeriesId(other)).isEmpty();
    }

    @Test
    void deletingASeriesDeletesItsRoundsPlayersAndPlays() {
        PlayerKey guest = PlayerKey.guest(UUID.randomUUID());
        UUID seriesId = seriesOf(guest).getId();
        players.saveAndFlush(FreeSeriesPlayer.of(seriesId, guest, null, NOW));
        plays.saveAndFlush(FreeSeriesPlay.start(seriesId, (short) 1, guest, Language.EN, 30, 40, NOW));

        jdbc.update("DELETE FROM free_series WHERE id = ?", seriesId);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM free_series_rounds WHERE series_id = ?", Integer.class, seriesId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM free_series_players WHERE series_id = ?", Integer.class, seriesId))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM free_series_plays WHERE series_id = ?", Integer.class, seriesId))
                .isZero();
    }

    /** A series of two rounds; photo_id may be null, the plays point at the round. */
    private FreeSeries seriesOf(PlayerKey creator) {
        FreeSeries saved = series.saveAndFlush(FreeSeries.create(creator, 5, NOW, DAY));
        for (short position = 1; position <= 2; position++) {
            rounds.saveAndFlush(FreeSeriesRound.builder()
                    .seriesId(saved.getId())
                    .position(position)
                    .build());
        }
        return saved;
    }

    private User saveUser() {
        int key = KEYS.incrementAndGet();
        return users.saveAndFlush(User.builder()
                .email("series" + key + "@x.com")
                .username("series" + key)
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build());
    }
}
