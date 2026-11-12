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

import app.ageguessr.features.game.GameFixtures;
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
            new RevealHintHandler(rounds, plays, GameFixtures.views(hintRepository));
    private final PlayerKey guest = PlayerKey.guest(UUID.randomUUID());
    private final List<Hint> offered =
            hints(5, HintCategory.MUSIC, HintCategory.FILM, HintCategory.SPORT, HintCategory.WORLD);

    @BeforeEach
    void setUp() {
        when(rounds.playable(TODAY, (short) 1)).thenReturn(playableRound((short) 1));
        when(hintRepository.findEligible(anyInt(), any(), any())).thenReturn(offered);
        when(hintRepository.findAllById(any())).thenAnswer(call -> {
            List<UUID> ids = call.getArgument(0);
            return offered.stream().filter(hint -> ids.contains(hint.getId())).toList();
        });
    }

    @Test
    void givesOneHintPerCategoryChosenInTheOrderOfTheCategoriesAndKeepsWhichOnes() {
        GamePlay play = startedPlay();

        RoundView view = handler.handle(guest, TODAY, (short) 1, command(HintCategory.WORLD, HintCategory.MUSIC, HintCategory.FILM));

        assertThat(play.getHintCategories()).containsExactly(HintCategory.MUSIC, HintCategory.FILM, HintCategory.WORLD);
        assertThat(play.getHintIds()).hasSize(3);
        for (int i = 0; i < 3; i++) {
            HintCategory category = play.getHintCategories().get(i);
            UUID id = play.getHintIds().get(i);
            assertThat(offered).filteredOn(hint -> hint.getId().equals(id)).extracting(Hint::getCategory).containsExactly(category);
        }
        verify(plays).saveAndFlush(play);
        assertThat(view.step()).isEqualTo(RoundStep.AWAITING_SECOND_GUESS);
        assertThat(view.hints())
                .extracting(RoundView.ShownHint::category)
                .containsExactly(HintCategory.MUSIC, HintCategory.FILM, HintCategory.WORLD);
    }

    @Test
    void drawsTheSameHintOfACategoryWhateverTheOthersChosen() {
        GamePlay first = startedPlay();
        handler.handle(guest, TODAY, (short) 1, command(HintCategory.MUSIC, HintCategory.FILM, HintCategory.SPORT));
        GamePlay second = startedPlay();
        handler.handle(guest, TODAY, (short) 1, command(HintCategory.MUSIC, HintCategory.WORLD, HintCategory.SPORT));

        assertThat(second.progress().hintIdOf(HintCategory.MUSIC)).isEqualTo(first.progress().hintIdOf(HintCategory.MUSIC));
        assertThat(second.progress().hintIdOf(HintCategory.SPORT)).isEqualTo(first.progress().hintIdOf(HintCategory.SPORT));
    }

    @Test
    void refusesAnotherNumberOfCategoriesThanTheRoundAsks() {
        startedPlay();

        for (RevealHintCommand wrong : List.of(
                command(HintCategory.MUSIC, HintCategory.FILM),
                command(HintCategory.MUSIC, HintCategory.FILM, HintCategory.SPORT, HintCategory.WORLD),
                command(HintCategory.MUSIC, HintCategory.MUSIC, HintCategory.FILM))) {
            assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, wrong))
                    .extracting("errorCode")
                    .isEqualTo("HINT_COUNT");
        }
        verify(plays, never()).saveAndFlush(any());
    }

    @Test
    void asksForEveryCategoryWhenFewerThanThreeAreOffered() {
        when(hintRepository.findEligible(anyInt(), any(), any())).thenReturn(hints(5, HintCategory.MUSIC, HintCategory.FILM));
        GamePlay play = startedPlay();

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, command(HintCategory.MUSIC)))
                .extracting("errorCode")
                .isEqualTo("HINT_COUNT");
        handler.handle(guest, TODAY, (short) 1, command(HintCategory.FILM, HintCategory.MUSIC));

        assertThat(play.getHintCategories()).containsExactly(HintCategory.MUSIC, HintCategory.FILM);
    }

    @Test
    void refusesHintsBeforeTheFirstGuess() {
        when(plays.findOf(guest, TODAY, (short) 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, threeCategories()))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
    }

    @Test
    void refusesToChooseTwice() {
        GamePlay play = startedPlay();
        play.revealHints(List.of(HintCategory.MUSIC), List.of(offered.getFirst().getId()));

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, threeCategories()))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
    }

    @Test
    void refusesACategoryNotOffered() {
        startedPlay();

        assertThatThrownBy(() -> handler.handle(
                        guest, TODAY, (short) 1, command(HintCategory.MUSIC, HintCategory.FILM, HintCategory.CELEBRITIES)))
                .extracting("errorCode")
                .isEqualTo("CATEGORY_UNAVAILABLE");
        verify(plays, never()).saveAndFlush(any());
    }

    @Test
    void refusesTheLoserOfTwoChoicesMadeAtOnce() {
        startedPlay();
        when(plays.saveAndFlush(any())).thenThrow(new ObjectOptimisticLockingFailureException(GamePlay.class, "id"));

        assertThatThrownBy(() -> handler.handle(guest, TODAY, (short) 1, threeCategories()))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
    }

    private GamePlay startedPlay() {
        GamePlay play = started(guest, (short) 1, 30);
        when(plays.findOf(guest, TODAY, (short) 1)).thenReturn(Optional.of(play));
        return play;
    }

    private static RevealHintCommand threeCategories() {
        return command(HintCategory.MUSIC, HintCategory.FILM, HintCategory.SPORT);
    }

    private static RevealHintCommand command(HintCategory... categories) {
        return new RevealHintCommand(List.of(categories));
    }
}
