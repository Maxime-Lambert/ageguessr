package app.ageguessr.features.game.GetTodayGame;

import static app.ageguessr.features.game.GameFixtures.AGE;
import static app.ageguessr.features.game.GameFixtures.CLOCK;
import static app.ageguessr.features.game.GameFixtures.NOW;
import static app.ageguessr.features.game.GameFixtures.TODAY;
import static app.ageguessr.features.game.GameFixtures.neutralizedRound;
import static app.ageguessr.features.game.GameFixtures.playableRound;
import static app.ageguessr.features.game.GameFixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.ageguessr.features.game.GameFixtures;
import app.ageguessr.features.game.DrawDailyGame.DrawDailyGameHandler;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GameProperties;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.GameScore;
import app.ageguessr.features.game.HintPicker;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundStep;
import app.ageguessr.features.game.RoundView;
import app.ageguessr.features.game.RoundViews;
import app.ageguessr.features.game.Scoring;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetTodayGameHandlerTest {

    private final DrawDailyGameHandler draw = mock(DrawDailyGameHandler.class);
    private final GameRounds rounds = mock(GameRounds.class);
    private final GamePlayRepository plays = mock(GamePlayRepository.class);
    private final HintRepository hintRepository = mock(HintRepository.class);
    private final RoundViews views = GameFixtures.views(hintRepository);
    private final PlayerKey guest = PlayerKey.guest(UUID.randomUUID());

    @Test
    void scoresTheFinishedPlayableRoundsOnlyOutOfAThousandEach() {
        when(rounds.of(TODAY))
                .thenReturn(List.of(playableRound((short) 1), playableRound((short) 2), neutralizedRound((short) 3)));
        GamePlay exact = started(guest, (short) 1, AGE);
        GamePlay finished = started(guest, (short) 2, 30);
        finished.revealHints(List.of(HintCategory.MUSIC), List.of(UUID.randomUUID()));
        finished.finishWithSecondGuess(38, AGE, NOW);
        GamePlay outOfTheScore = started(guest, (short) 3, AGE);
        when(plays.findOf(guest, TODAY)).thenReturn(List.of(exact, finished, outOfTheScore));

        TodayGameResponse response = handler(false).handle(Optional.of(guest));

        assertThat(response.date()).isEqualTo(TODAY);
        assertThat(response.rounds()).extracting(RoundView::position).containsExactly((short) 1, (short) 2, (short) 3);
        assertThat(response.score())
                .isEqualTo(new GameScore(
                        1000 + Scoring.points(30, AGE) + Scoring.points(38, AGE), 2000, true));
    }

    @Test
    void isNotCompleteWhileAPlayableRoundIsLeft() {
        when(rounds.of(TODAY)).thenReturn(List.of(playableRound((short) 1), playableRound((short) 2)));
        when(plays.findOf(guest, TODAY)).thenReturn(List.of(started(guest, (short) 1, AGE)));

        TodayGameResponse response = handler(false).handle(Optional.of(guest));

        assertThat(response.score()).isEqualTo(new GameScore(1000, 2000, false));
    }

    @Test
    void showsAPlayerWithoutCookieEveryRoundNotStarted() {
        when(rounds.of(TODAY)).thenReturn(List.of(playableRound((short) 1)));

        TodayGameResponse response = handler(false).handle(Optional.empty());

        assertThat(response.rounds()).extracting(RoundView::step).containsExactly(RoundStep.NOT_STARTED);
        assertThat(response.score()).isEqualTo(new GameScore(0, 1000, false));
        verifyNoInteractions(plays);
    }

    @Test
    void drawsTheDayFirstWhenTheDrawIsOn() {
        when(rounds.of(TODAY)).thenReturn(List.of());

        handler(true).handle(Optional.empty());

        verify(draw).drawIfMissing(TODAY);
    }

    @Test
    void neverDrawsWhenTheDrawIsOff() {
        when(rounds.of(TODAY)).thenReturn(List.of());

        TodayGameResponse response = handler(false).handle(Optional.empty());

        verify(draw, never()).drawIfMissing(any());
        verify(hintRepository, never()).findEligible(anyInt(), any(), any());
        assertThat(response.rounds()).isEmpty();
    }

    private GetTodayGameHandler handler(boolean drawEnabled) {
        return new GetTodayGameHandler(draw, new GameProperties(3, 3, drawEnabled), rounds, plays, views, CLOCK);
    }
}
