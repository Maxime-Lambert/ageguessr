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
import app.ageguessr.features.game.PlayerHistories;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.shared.i18n.Language;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** The histories of a group in two queries: only the given accounts' daily rounds. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PlayerHistoriesIntegrationTest {

    @Autowired
    private PlayerHistories histories;

    @Autowired
    private GamePlayRepository plays;

    @Autowired
    private DailyRoundRepository rounds;

    @Autowired
    private UserRepository users;

    @Autowired
    private Clock clock;

    @Test
    void readsTheGivenAccountsOnly() {
        LocalDate today = LocalDate.now(clock);
        UUID alice = user();
        UUID bob = user();
        UUID idle = user();
        play(today, PlayerKey.account(alice));
        play(today.minusDays(1), PlayerKey.account(alice));
        play(today, PlayerKey.account(bob));
        play(today, PlayerKey.account(user()));
        play(today, PlayerKey.guest(UUID.randomUUID()));

        Map<UUID, PlayerHistories.Record> records = histories.of(List.of(alice, bob, idle));

        assertThat(records).containsOnlyKeys(alice, bob, idle);
        assertThat(records.get(alice).plays()).hasSize(2);
        assertThat(records.get(alice).result().stats().gamesPlayed()).isEqualTo(2);
        assertThat(records.get(alice).result().stats().currentStreak()).isEqualTo(2);
        assertThat(records.get(bob).result().days()).hasSize(1);
        assertThat(records.get(idle).plays()).isEmpty();
        assertThat(records.get(idle).result().stats().gamesPlayed()).isZero();
        assertThat(histories.of(List.of())).isEmpty();
    }

    private UUID user() {
        return users.saveAndFlush(User.builder()
                        .email("histories-" + UUID.randomUUID() + "@x.com")
                        .username("hs" + UUID.randomUUID().toString().substring(0, 8))
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
