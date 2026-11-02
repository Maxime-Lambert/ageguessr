package app.ageguessr.features.game;

import static app.ageguessr.features.game.GameFixtures.AGE;
import static app.ageguessr.features.game.GameFixtures.HINT_YEAR;
import static app.ageguessr.features.game.GameFixtures.NOW;
import static app.ageguessr.features.game.GameFixtures.QID;
import static app.ageguessr.features.game.GameFixtures.hints;
import static app.ageguessr.features.game.GameFixtures.neutralizedRound;
import static app.ageguessr.features.game.GameFixtures.playableRound;
import static app.ageguessr.features.game.GameFixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoundViewsTest {

    private final HintRepository hintRepository = mock(HintRepository.class);
    private final RoundViews views = new RoundViews(new HintPicker(hintRepository));
    private final PlayerKey guest = PlayerKey.guest(UUID.randomUUID());

    @Test
    void showsOnlyTheImageOfARoundNotStarted() {
        GameRound round = playableRound((short) 1);

        RoundView view = views.of(round, null);

        assertThat(view.step()).isEqualTo(RoundStep.NOT_STARTED);
        assertThat(view.imageUrl()).isEqualTo("/api/images/" + round.photo().getPublicId());
        assertThat(view)
                .extracting(RoundView::firstGuess, RoundView::direction, RoundView::categories, RoundView::hint, RoundView::result)
                .containsOnlyNulls();
    }

    @Test
    void showsTheSideAndTheCategoriesAfterAWrongFirstGuessButNotTheAnswer() {
        when(hintRepository.findEligible(HINT_YEAR, Language.EN.audiences(), QID))
                .thenReturn(hints(HintCategory.FILM, 5));

        RoundView view = views.of(playableRound((short) 1), started(guest, (short) 1, 30));

        assertThat(view.step()).isEqualTo(RoundStep.AWAITING_HINT);
        assertThat(view.firstGuess()).isEqualTo(30);
        assertThat(view.direction()).isEqualTo(Direction.OLDER);
        assertThat(view.categories()).containsExactly(HintCategory.FILM);
        assertThat(view.result()).isNull();
    }

    @Test
    void goesStraightToTheSecondGuessWhenNoCategoryCanBeOffered() {
        when(hintRepository.findEligible(anyInt(), any(), anyString())).thenReturn(hints(HintCategory.FILM, 4));

        RoundView view = views.of(playableRound((short) 1), started(guest, (short) 1, 50));

        assertThat(view.step()).isEqualTo(RoundStep.AWAITING_SECOND_GUESS);
        assertThat(view.direction()).isEqualTo(Direction.YOUNGER);
        assertThat(view.categories()).isEmpty();
    }

    @Test
    void showsTheHintWithoutItsYearWhileAwaitingTheSecondGuess() {
        Hint hint = hints(HintCategory.MUSIC, 1).getFirst();
        when(hintRepository.findById(hint.getId())).thenReturn(Optional.of(hint));
        GamePlay play = started(guest, (short) 1, 30);
        play.revealHint(HintCategory.MUSIC, hint.getId());

        RoundView view = views.of(playableRound((short) 1), play);

        assertThat(view.step()).isEqualTo(RoundStep.AWAITING_SECOND_GUESS);
        assertThat(view.hint()).isEqualTo(new RoundView.ShownHint(HintCategory.MUSIC, "Hint 0"));
        assertThat(view.categories()).isNull();
    }

    @Test
    void revealsAFinishedRound() {
        Hint hint = hints(HintCategory.MUSIC, 1).getFirst();
        when(hintRepository.findById(hint.getId())).thenReturn(Optional.of(hint));
        GamePlay play = started(guest, (short) 1, 30);
        play.revealHint(HintCategory.MUSIC, hint.getId());
        play.secondGuess(AGE, AGE, NOW);

        RoundView view = views.of(playableRound((short) 1), play);

        assertThat(view.step()).isEqualTo(RoundStep.FINISHED);
        assertThat(view.direction()).isNull();
        assertThat(view.result().age()).isEqualTo(AGE);
        assertThat(view.result().hint().year()).isEqualTo(HINT_YEAR);
    }

    @Test
    void showsNothingOfANeutralizedRoundEvenOnceFinished() {
        RoundView view = views.of(neutralizedRound((short) 2), started(guest, (short) 2, AGE));

        assertThat(view.status()).isEqualTo(DailyRoundStatus.NEUTRALIZED);
        assertThat(view.step()).isEqualTo(RoundStep.FINISHED);
        assertThat(view)
                .extracting(RoundView::imageUrl, RoundView::firstGuess, RoundView::result)
                .containsOnlyNulls();
    }

    @Test
    void offersTheHintsInTheLanguageOfTheRound() {
        when(hintRepository.findEligible(HINT_YEAR, Language.FR.audiences(), QID))
                .thenReturn(hints(HintCategory.SPORT, 6));

        assertThat(views.offer(playableRound((short) 1), Language.FR).categories())
                .isEqualTo(List.of(HintCategory.SPORT));
    }
}
