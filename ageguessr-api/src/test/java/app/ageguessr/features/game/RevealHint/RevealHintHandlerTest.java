package app.ageguessr.features.game.RevealHint;

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

import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.HintPicker;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundStep;
import app.ageguessr.features.game.RoundView;
import app.ageguessr.features.game.RoundViews;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class RevealHintHandlerTest {

    private final GameRounds rounds = mock(GameRounds.class);
    private final GamePlayRepository plays = mock(GamePlayRepository.class);
    private final HintRepository hintRepository = mock(HintRepository.class);
    private final RevealHintHandler handler =
            new RevealHintHandler(rounds, plays, new RoundViews(new HintPicker(hintRepository)));
    private final PlayerKey guest = PlayerKey.guest(UUID.randomUUID());
    private final List<Hint> music = hints(HintCategory.MUSIC, 5);

    @BeforeEach
    void setUp() {
        when(rounds.playable(TODAY, (short) 1)).thenReturn(playableRound((short) 1));
        when(hintRepository.findEligible(anyInt(), any(), any())).thenReturn(music);
        music.forEach(hint -> when(hintRepository.findById(hint.getId())).thenReturn(Optional.of(hint)));
    }

    @Test
    void givesAHintOfTheCategoryAndKeepsWhichOne() {
        GamePlay play = started(guest, (short) 1, 30);
        when(plays.findOf(guest, TODAY, (short) 1)).thenReturn(Optional.of(play));

        RoundView view = handler.handle(guest, TODAY, (short) 1, new RevealHintCommand(HintCategory.MUSIC));

        assertThat(play.getHintCategory()).isEqualTo(HintCategory.MUSIC);
        assertThat(music).extracting(Hint::getId).contains(play.getHintId());
        verify(plays).saveAndFlush(play);
        assertThat(view.step()).isEqualTo(RoundStep.AWAITING_SECOND_GUESS);
        assertThat(view.hint().category()).isEqualTo(HintCategory.MUSIC);
    }

    @Test
    void refusesAHintBeforeTheFirstGuess() {
        when(plays.findOf(guest, TODAY, (short) 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, new RevealHintCommand(HintCategory.MUSIC)))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
    }

    @Test
    void refusesASecondHint() {
        GamePlay play = started(guest, (short) 1, 30);
        play.revealHint(HintCategory.MUSIC, music.getFirst().getId());
        when(plays.findOf(guest, TODAY, (short) 1)).thenReturn(Optional.of(play));

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, new RevealHintCommand(HintCategory.MUSIC)))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
    }

    @Test
    void refusesACategoryNotOffered() {
        GamePlay play = started(guest, (short) 1, 30);
        when(plays.findOf(guest, TODAY, (short) 1)).thenReturn(Optional.of(play));

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, new RevealHintCommand(HintCategory.SPORT)))
                .extracting("errorCode")
                .isEqualTo("CATEGORY_UNAVAILABLE");
        verify(plays, never()).saveAndFlush(any());
    }

    @Test
    void refusesTheLoserOfTwoHintsChosenAtOnce() {
        when(plays.findOf(guest, TODAY, (short) 1)).thenReturn(Optional.of(started(guest, (short) 1, 30)));
        when(plays.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(GamePlay.class, "id"));

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, new RevealHintCommand(HintCategory.MUSIC)))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
    }
}
