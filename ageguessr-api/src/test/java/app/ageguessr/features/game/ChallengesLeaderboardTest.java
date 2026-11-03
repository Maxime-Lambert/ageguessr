package app.ageguessr.features.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.ageguessr.shared.i18n.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChallengesLeaderboardTest {

    private static final Instant NOW = Instant.parse("2026-11-03T08:00:00Z");
    private static final int AGE = 40;

    private final FreeSeriesPlayRepository plays = mock(FreeSeriesPlayRepository.class);
    private final Challenges challenges = new Challenges(
            mock(FreeSeriesRepository.class), mock(FreeSeriesPlayerRepository.class), plays, null, null);
    private final FreeSeries series = FreeSeries.create(PlayerKey.guest(UUID.randomUUID()), 5, NOW, LocalDate.of(2026, 11, 3));
    private final List<FreeSeriesPlay> played = new ArrayList<>();

    @Test
    void ranksByPointsThenByWhoFinishedFirstAndSharesARankOnEqualPoints() {
        PlayerKey ana = PlayerKey.account(UUID.randomUUID());
        PlayerKey bob = PlayerKey.guest(UUID.randomUUID());
        PlayerKey cy = PlayerKey.guest(UUID.randomUUID());
        PlayerKey dee = PlayerKey.guest(UUID.randomUUID());
        finish(ana, 40, 40, 60); // 2 000
        finish(bob, 30, 40, 10); // less, finished first
        finish(cy, 40, 40, 30); // 2 000, before ana
        finish(dee, 30, 40, 20); // as bob, after him

        var board = challenges.leaderboard(series, rounds(2), names(ana, bob, cy, dee), bob);

        assertThat(board).extracting(ChallengeResponse.LeaderboardEntry::name).containsExactly("cy", "ana", "bob", "dee");
        assertThat(board).extracting(ChallengeResponse.LeaderboardEntry::rank).containsExactly(1, 1, 3, 3);
        assertThat(board).extracting(ChallengeResponse.LeaderboardEntry::max).containsOnly(2000);
        assertThat(board.get(1).guest()).isFalse();
        assertThat(board.get(2).guest()).isTrue();
        assertThat(board).extracting(ChallengeResponse.LeaderboardEntry::you).containsExactly(false, false, true, false);
    }

    @Test
    void leavesOutWhoHasNotFinishedAndCountsOnlyThePlayableRounds() {
        PlayerKey ana = PlayerKey.guest(UUID.randomUUID());
        PlayerKey bob = PlayerKey.guest(UUID.randomUUID());
        finish(ana, 40, 40, 10);
        played.add(FreeSeriesPlay.start(series.getId(), (short) 1, bob, Language.EN, 30, AGE, NOW));
        List<GameRound> rounds = new ArrayList<>(rounds(2));
        rounds.set(1, new GameRound(LocalDate.of(2026, 11, 3), (short) 2, DailyRoundStatus.NEUTRALIZED, null, null));

        var board = challenges.leaderboard(series, rounds, names(ana, bob), ana);

        assertThat(board).hasSize(1);
        assertThat(board.getFirst().points()).isEqualTo(1000);
        assertThat(board.getFirst().max()).isEqualTo(1000);
    }

    /** Exact guesses ({@code AGE}) score 1 000 each; others are first guesses then {@code AGE}. */
    private void finish(PlayerKey player, int first, int second, int minutes) {
        Instant at = NOW.plusSeconds(minutes * 60L);
        for (short position = 1; position <= 2; position++) {
            int guess = position == 1 ? first : second;
            FreeSeriesPlay play = FreeSeriesPlay.start(series.getId(), position, player, Language.EN, guess, AGE, at);
            if (guess != AGE) {
                play.finishWithSecondGuess(AGE, AGE, at);
            }
            played.add(play);
        }
        when(plays.findBySeriesId(series.getId())).thenReturn(played);
    }

    private static List<GameRound> rounds(int count) {
        List<GameRound> rounds = new ArrayList<>();
        for (short position = 1; position <= count; position++) {
            rounds.add(new GameRound(LocalDate.of(2026, 11, 3), position, DailyRoundStatus.PLAYABLE, null, null));
        }
        return rounds;
    }

    private static Map<PlayerKey, FreeSeriesPlayerRepository.PlayerName> names(PlayerKey... players) {
        String[] labels = {"ana", "bob", "cy", "dee"};
        Map<PlayerKey, FreeSeriesPlayerRepository.PlayerName> names = new HashMap<>();
        for (int i = 0; i < players.length; i++) {
            PlayerKey player = players[i];
            String label = labels[i];
            names.put(player, new FreeSeriesPlayerRepository.PlayerName() {
                public UUID getUserId() {
                    return player.userId();
                }

                public UUID getGuestId() {
                    return player.guestId();
                }

                public String getName() {
                    return label;
                }
            });
        }
        return names;
    }
}
