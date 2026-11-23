package app.ageguessr.features.game.GetDayStats;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.shared.i18n.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DayStatsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);
    private static final Instant NOW = Instant.parse("2026-11-23T09:00:00Z");
    private static final int AGE = 40;
    private static final Set<Short> TWO_ROUNDS = Set.of((short) 1, (short) 2);

    private final List<GamePlay> plays = new ArrayList<>();

    /** Round 1 and 2: this first guess, then the right age as the second (or exact). */
    private PlayerKey finished(int firstGuess) {
        PlayerKey player = PlayerKey.guest(UUID.randomUUID());
        for (short position = 1; position <= 2; position++) {
            plays.add(play(player, position, firstGuess, true));
        }
        return player;
    }

    private static GamePlay play(PlayerKey player, short position, int firstGuess, boolean finish) {
        GamePlay play = GamePlay.start(DAY, position, player, Language.EN, firstGuess, AGE, NOW);
        if (finish && firstGuess != AGE) {
            play.revealHints(List.of(HintCategory.MUSIC), List.of(UUID.randomUUID()));
            play.finishWithSecondGuess(AGE, AGE, NOW);
        }
        return play;
    }

    @Test
    void saysOnlyHowManyFinishedUnderTheMinimum() {
        PlayerKey me = finished(AGE);
        for (int i = 0; i < DayStats.MINIMUM_PLAYERS - 2; i++) {
            finished(30);
        }

        DayStatsResponse stats = DayStats.of(plays, TWO_ROUNDS, me);

        assertThat(stats.players()).isEqualTo(DayStats.MINIMUM_PLAYERS - 1);
        assertThat(stats.betterThanPercent()).isNull();
        assertThat(stats.rounds()).isEmpty();
    }

    @Test
    void comparesTheTotalWithTheOthersWhoFinishedAndAveragesTheFirstGuesses() {
        // 1 exact player (me), 4 far below, 5 close: I beat the 9 others but ties count not.
        PlayerKey me = finished(AGE);
        for (int i = 0; i < 4; i++) {
            finished(20);
        }
        for (int i = 0; i < 5; i++) {
            finished(AGE);
        }

        DayStatsResponse stats = DayStats.of(plays, TWO_ROUNDS, me);

        assertThat(stats.players()).isEqualTo(10);
        // 4 of the 9 others are below; the 5 others found it too, a tie.
        assertThat(stats.betterThanPercent()).isEqualTo(44);
        // (6 × 40 + 4 × 20) / 10 = 32.
        assertThat(stats.rounds())
                .containsExactly(
                        new DayStatsResponse.RoundStats((short) 1, 32), new DayStatsResponse.RoundStats((short) 2, 32));
    }

    @Test
    void leavesOutWhoDidNotFinishTheDayAndTheWithdrawnRounds() {
        PlayerKey me = finished(AGE);
        for (int i = 0; i < DayStats.MINIMUM_PLAYERS - 1; i++) {
            finished(30);
        }
        // Someone who stopped at the hint of round 2: not counted, nor their guesses.
        PlayerKey quitter = PlayerKey.guest(UUID.randomUUID());
        plays.add(play(quitter, (short) 1, 90, true));
        plays.add(play(quitter, (short) 2, 90, false));
        // A round 3 withdrawn after some played it: out of the totals and the averages.
        plays.add(play(me, (short) 3, 99, false));

        DayStatsResponse stats = DayStats.of(plays, TWO_ROUNDS, me);

        assertThat(stats.players()).isEqualTo(10);
        assertThat(stats.betterThanPercent()).isEqualTo(100);
        assertThat(stats.rounds()).extracting(DayStatsResponse.RoundStats::position).containsExactly((short) 1, (short) 2);
        assertThat(stats.rounds().getFirst().averageFirstGuess()).isEqualTo(31);
    }
}
