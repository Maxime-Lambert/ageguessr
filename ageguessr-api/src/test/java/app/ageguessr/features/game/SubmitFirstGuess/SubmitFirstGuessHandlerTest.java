package app.ageguessr.features.game.SubmitFirstGuess;

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

import app.ageguessr.features.game.Direction;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.HintPicker;
import app.ageguessr.shared.i18n.Language;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundStep;
import app.ageguessr.features.game.RoundView;
import app.ageguessr.features.game.RoundViews;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class SubmitFirstGuessHandlerTest {

    private final GameRounds rounds = mock(GameRounds.class);
    private final GamePlayRepository plays = mock(GamePlayRepository.class);
    private final HintRepository hintRepository = mock(HintRepository.class);
    private final SubmitFirstGuessHandler handler =
            new SubmitFirstGuessHandler(rounds, plays, new RoundViews(new HintPicker(hintRepository)), CLOCK);
    private final PlayerKey guest = PlayerKey.guest(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        when(rounds.playable(TODAY, (short) 1)).thenReturn(playableRound((short) 1));
        when(plays.findOf(guest, TODAY, (short) 1)).thenReturn(Optional.empty());
        when(hintRepository.findEligible(anyInt(), any(), any())).thenReturn(hints(HintCategory.MUSIC, 5));
    }

    @Test
    void startsTheRoundAndTellsTheSideWithTheCategories() {
        RoundView view = handler.handle(guest, TODAY, (short) 1, new SubmitFirstGuessCommand(30, Language.FR));

        ArgumentCaptor<GamePlay> saved = ArgumentCaptor.forClass(GamePlay.class);
        verify(plays).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getGuestId()).isEqualTo(guest.guestId());
        assertThat(saved.getValue().getLanguage()).isEqualTo(Language.FR);
        assertThat(view.step()).isEqualTo(RoundStep.AWAITING_HINT);
        assertThat(view.direction()).isEqualTo(Direction.OLDER);
        assertThat(view.categories()).containsExactly(HintCategory.MUSIC);
        assertThat(view.result()).isNull();
    }

    @Test
    void endsTheRoundAtAThousandOnAnExactGuessWithoutAnyHint() {
        RoundView view = handler.handle(guest, TODAY, (short) 1, new SubmitFirstGuessCommand(AGE, Language.EN));

        assertThat(view.step()).isEqualTo(RoundStep.FINISHED);
        assertThat(view.result().points()).isEqualTo(1000);
        assertThat(view.result().hint()).isNull();
        verify(hintRepository, never()).findEligible(anyInt(), any(), any());
    }

    @Test
    void refusesASecondFirstGuess() {
        when(plays.findOf(guest, TODAY, (short) 1)).thenReturn(Optional.of(started(guest, (short) 1, 30)));

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, new SubmitFirstGuessCommand(35, Language.EN)))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
        verify(plays, never()).saveAndFlush(any());
    }

    @Test
    void refusesTheLoserOfTwoFirstGuessesSentAtOnce() {
        when(plays.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("game_plays_guest_round_unique_idx"));

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, new SubmitFirstGuessCommand(35, Language.EN)))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
    }
}
