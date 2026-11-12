package app.ageguessr.features.game.SubmitSecondGuess;

import static app.ageguessr.features.game.GameFixtures.AGE;
import static app.ageguessr.features.game.GameFixtures.CLOCK;
import static app.ageguessr.features.game.GameFixtures.TODAY;
import static app.ageguessr.features.game.GameFixtures.hints;
import static app.ageguessr.features.game.GameFixtures.playableRound;
import static app.ageguessr.features.game.GameFixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.game.GameFixtures;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GameRounds;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class SubmitSecondGuessHandlerTest {

    private final GameRounds rounds = mock(GameRounds.class);
    private final GamePlayRepository plays = mock(GamePlayRepository.class);
    private final HintRepository hintRepository = mock(HintRepository.class);
    private final SubmitSecondGuessHandler handler =
            new SubmitSecondGuessHandler(rounds, plays, GameFixtures.views(hintRepository), CLOCK);
    private final PlayerKey guest = PlayerKey.guest(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        when(rounds.playable(TODAY, (short) 1)).thenReturn(playableRound((short) 1));
        when(hintRepository.findEligible(anyInt(), any(), any())).thenReturn(hints(HintCategory.MUSIC, 5));
    }

    @Test
    void endsTheRoundAfterTheHintAndRevealsIt() {
        GamePlay play = withPlay(30);
        play.revealHints(List.of(HintCategory.MUSIC), List.of(UUID.randomUUID()));

        RoundView view = handler.handle(guest, TODAY, (short) 1, new SubmitSecondGuessCommand(38));

        verify(plays).saveAndFlush(play);
        assertThat(view.step()).isEqualTo(RoundStep.FINISHED);
        assertThat(view.result().age()).isEqualTo(AGE);
        assertThat(view.result().points()).isEqualTo(Scoring.points(30, AGE) + Scoring.points(38, AGE));
    }

    @Test
    void refusesTheSecondGuessBeforeTheHintWhenACategoryIsOffered() {
        withPlay(30);

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, new SubmitSecondGuessCommand(38)))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
        verify(plays, never()).saveAndFlush(any());
    }

    @Test
    void acceptsTheSecondGuessWithoutAHintWhenNoCategoryIsOffered() {
        when(hintRepository.findEligible(anyInt(), any(), any())).thenReturn(List.of());
        withPlay(30);

        RoundView view = handler.handle(guest, TODAY, (short) 1, new SubmitSecondGuessCommand(38));

        assertThat(view.step()).isEqualTo(RoundStep.FINISHED);
        assertThat(view.result().hints()).isEmpty();
    }

    @Test
    void refusesTheSecondGuessBeforeTheFirst() {
        when(plays.findOf(guest, TODAY, (short) 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, new SubmitSecondGuessCommand(38)))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
    }

    @Test
    void refusesASecondGuessOnAFinishedRound() {
        withPlay(AGE);

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, new SubmitSecondGuessCommand(38)))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
    }

    @Test
    void refusesTheLoserOfTwoSecondGuessesSentAtOnce() {
        withPlay(30).revealHints(List.of(HintCategory.MUSIC), List.of(UUID.randomUUID()));
        when(plays.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(GamePlay.class, "id"));

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, new SubmitSecondGuessCommand(38)))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
    }

    private GamePlay withPlay(int firstGuess) {
        GamePlay play = started(guest, (short) 1, firstGuess);
        when(plays.findOf(guest, TODAY, (short) 1)).thenReturn(Optional.of(play));
        return play;
    }
}
