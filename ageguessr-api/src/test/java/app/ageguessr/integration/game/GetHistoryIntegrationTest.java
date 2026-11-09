package app.ageguessr.integration.game;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundId;
import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GetHistory.GetHistoryHandler;
import app.ageguessr.features.game.GetHistory.HistoryResponse;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.shared.i18n.Language;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * The history's queries against Postgres: an account's rows only, pages of 30 days. The
 * rounds here have no photo, so they read as neutralized: finished, they keep their
 * points, and never show a name.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class GetHistoryIntegrationTest {

    @Autowired
    private GetHistoryHandler handler;

    @Autowired
    private GamePlayRepository plays;

    @Autowired
    private DailyRoundRepository rounds;

    @Autowired
    private UserRepository users;

    @Autowired
    private Clock clock;

    @Test
    void readsTheAccountsDaysOnlyInPagesOfThirty() {
        LocalDate today = LocalDate.now(clock);
        UUID account = user();
        for (int i = 0; i < 31; i++) {
            play(today.minusDays(i), PlayerKey.account(account));
        }
        play(today.minusDays(40), PlayerKey.guest(UUID.randomUUID()));
        play(today.minusDays(41), PlayerKey.account(user()));

        HistoryResponse first = handler.handle(account, 0);
        HistoryResponse second = handler.handle(account, 1);

        assertThat(first.days()).hasSize(30);
        assertThat(first.days().getFirst().date()).isEqualTo(today);
        assertThat(first.hasMore()).isTrue();
        assertThat(second.days()).extracting(HistoryResponse.Day::date).containsExactly(today.minusDays(30));
        assertThat(second.hasMore()).isFalse();
        assertThat(first.stats().currentStreak()).isEqualTo(31);
        assertThat(first.stats().gamesPlayed()).isEqualTo(31);
        HistoryResponse.Round round = first.days().getFirst().rounds().getFirst();
        assertThat(round.neutralized()).isTrue();
        assertThat(round.name()).isNull();
        assertThat(round.points()).isEqualTo(1000);
    }

    @Test
    void aPageBeyondTheEndIsEmpty() {
        UUID account = user();
        play(LocalDate.now(clock), PlayerKey.account(account));

        HistoryResponse beyond = handler.handle(account, Integer.MAX_VALUE);

        assertThat(beyond.days()).isEmpty();
        assertThat(beyond.hasMore()).isFalse();
    }

    private UUID user() {
        return users.saveAndFlush(User.builder()
                        .email("history-" + UUID.randomUUID() + "@x.com")
                        .username("hist" + UUID.randomUUID().toString().substring(0, 8))
                        .passwordHash("hash")
                        .createdAt(Instant.now())
                        .build())
                .getId();
    }

    /** An exact first guess: a finished round worth 1 000. */
    private void play(LocalDate day, PlayerKey player) {
        if (!rounds.existsById(new DailyRoundId(day, (short) 1))) {
            rounds.saveAndFlush(DailyRound.builder().gameDate(day).position((short) 1).drawnAt(Instant.now()).build());
        }
        plays.saveAndFlush(GamePlay.start(day, (short) 1, player, Language.EN, 40, 40, Instant.now()));
    }
}
