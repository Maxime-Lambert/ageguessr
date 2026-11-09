package app.ageguessr.integration.game;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.game.ClaimGuestPlays.ClaimGuestPlaysHandler;
import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundId;
import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
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

/** Moving a guest's daily games to an account, against the real unique indexes of V13. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class ClaimGuestPlaysIntegrationTest {

    @Autowired
    private ClaimGuestPlaysHandler handler;

    @Autowired
    private GamePlayRepository plays;

    @Autowired
    private DailyRoundRepository rounds;

    @Autowired
    private UserRepository users;

    @Autowired
    private Clock clock;

    @Test
    void movesTheGuestRoundsAndKeepsTheAccountsOwnRound() {
        LocalDate today = LocalDate.now(clock);
        UUID guest = UUID.randomUUID();
        UUID account = user();
        GamePlay yesterday = play(today.minusDays(1), 1, PlayerKey.guest(guest), 30);
        GamePlay guestToday = play(today, 1, PlayerKey.guest(guest), 31);
        GamePlay accountToday = play(today, 1, PlayerKey.account(account), 60);

        assertThat(handler.handle(guest, account)).isEqualTo(1);

        assertThat(plays.findById(guestToday.getId())).isEmpty();
        assertThat(plays.findById(accountToday.getId())).get().extracting(p -> p.getProgress().getFirstGuess()).isEqualTo(60);
        GamePlay moved = plays.findById(yesterday.getId()).orElseThrow();
        assertThat(moved.getUserId()).isEqualTo(account);
        assertThat(moved.getGuestId()).isNull();
    }

    @Test
    void leavesOtherPlayersAloneAndFindsNothingTheSecondTime() {
        LocalDate today = LocalDate.now(clock);
        UUID guest = UUID.randomUUID();
        UUID account = user();
        UUID otherAccount = user();
        play(today, 1, PlayerKey.guest(guest), 30);
        GamePlay otherGuest = play(today, 1, PlayerKey.guest(UUID.randomUUID()), 40);
        GamePlay ofOtherAccount = play(today, 1, PlayerKey.account(otherAccount), 50);

        assertThat(handler.handle(guest, account)).isEqualTo(1);
        assertThat(handler.handle(guest, account)).isZero();

        assertThat(plays.findById(otherGuest.getId()).orElseThrow().getUserId()).isNull();
        assertThat(plays.findById(ofOtherAccount.getId()).orElseThrow().getUserId()).isEqualTo(otherAccount);
        assertThat(plays.findOf(PlayerKey.account(account), today)).hasSize(1);
    }

    @Test
    void keepsTheReportMarkOfAMovedRound() {
        LocalDate today = LocalDate.now(clock);
        UUID guest = UUID.randomUUID();
        UUID account = user();
        // An exact first guess finishes the round, which a report needs.
        GamePlay reported = play(today, 1, PlayerKey.guest(guest), 40);
        reported.getProgress().reportPhoto();
        plays.saveAndFlush(reported);

        handler.handle(guest, account);

        assertThat(plays.findById(reported.getId()).orElseThrow().getProgress().reportPhoto()).isFalse();
    }

    private UUID user() {
        return users.saveAndFlush(User.builder()
                        .email("claim-" + UUID.randomUUID() + "@x.com")
                        .username("claim" + UUID.randomUUID().toString().substring(0, 8))
                        .passwordHash("hash")
                        .createdAt(Instant.now())
                        .build())
                .getId();
    }

    private GamePlay play(LocalDate day, int position, PlayerKey player, int guess) {
        if (!rounds.existsById(new DailyRoundId(day, (short) position))) {
            rounds.saveAndFlush(
                    DailyRound.builder().gameDate(day).position((short) position).drawnAt(Instant.now()).build());
        }
        return plays.saveAndFlush(GamePlay.start(day, (short) position, player, Language.EN, guess, 40, Instant.now()));
    }
}
