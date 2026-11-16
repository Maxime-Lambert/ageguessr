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
import app.ageguessr.shared.i18n.Language;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.PurgeGuestPlays.PurgeGuestPlaysHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** The retention of the guest games (7 days in the test properties, as in prod). */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class PurgeGuestPlaysIntegrationTest {

    @Autowired
    private PurgeGuestPlaysHandler handler;

    @Autowired
    private GamePlayRepository plays;

    @Autowired
    private DailyRoundRepository rounds;

    @Autowired
    private UserRepository users;

    @Autowired
    private Clock clock;

    @Test
    void deletesTheGuestRoundsPlayedMoreThanSevenDaysAgoOnly() {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        GamePlay eightDays = play(today.minusDays(8), PlayerKey.guest(UUID.randomUUID()), now.minus(Duration.ofDays(8)));
        GamePlay sixDays = play(today.minusDays(6), PlayerKey.guest(UUID.randomUUID()), now.minus(Duration.ofDays(6)));
        GamePlay ofToday = play(today, PlayerKey.guest(UUID.randomUUID()), now);
        // A day of a month ago, caught up from the calendar yesterday: kept.
        GamePlay caughtUp = play(today.minusDays(30), PlayerKey.guest(UUID.randomUUID()), now.minus(Duration.ofDays(1)));
        User user = users.saveAndFlush(User.builder()
                .email("purge-" + UUID.randomUUID() + "@x.com")
                .username("purge" + UUID.randomUUID().toString().substring(0, 8))
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build());
        GamePlay ofAccount =
                play(today.minusDays(8), PlayerKey.account(user.getId()), now.minus(Duration.ofDays(8)));

        assertThat(handler.purge()).isEqualTo(1);

        assertThat(plays.findAll())
                .extracting(GamePlay::getId)
                .containsExactlyInAnyOrder(sixDays.getId(), ofToday.getId(), caughtUp.getId(), ofAccount.getId())
                .doesNotContain(eightDays.getId());
    }

    private GamePlay play(LocalDate day, PlayerKey player, Instant startedAt) {
        if (!rounds.existsById(new DailyRoundId(day, (short) 1))) {
            rounds.saveAndFlush(DailyRound.builder().gameDate(day).position((short) 1).drawnAt(Instant.now()).build());
        }
        return plays.saveAndFlush(GamePlay.start(day, (short) 1, player, Language.EN, 30, 40, startedAt));
    }
}
