package app.ageguessr.features.game;

import app.ageguessr.shared.i18n.Language;
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
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RoundViewsTest {

    private final HintRepository hintRepository = mock(HintRepository.class);
    private final RoundViews views = GameFixtures.views(hintRepository);
    private final PlayerKey guest = PlayerKey.guest(UUID.randomUUID());

    @Test
    void showsOnlyTheImageOfARoundNotStarted() {
        GameRound round = playableRound((short) 1);

        RoundView view = views.of(round, null);

        assertThat(view.step()).isEqualTo(RoundStep.NOT_STARTED);
        assertThat(view.imageUrl()).isEqualTo("/api/images/" + round.photo().getPublicId());
        assertThat(view)
                .extracting(RoundView::firstGuess, RoundView::direction, RoundView::gap, RoundView::range, RoundView::categories, RoundView::hintsToPick, RoundView::hints, RoundView::result)
                .containsOnlyNulls();
    }

    @Test
    void showsTheSideAndTheCategoriesAfterAWrongFirstGuessButNotTheAnswer() {
        when(hintRepository.findEligible(HINT_YEAR, HintPicker.audiences(Language.EN), QID))
                .thenReturn(hints(HintCategory.FILM, 5));

        RoundView view = views.of(playableRound((short) 1), started(guest, (short) 1, 30));

        assertThat(view.step()).isEqualTo(RoundStep.AWAITING_HINT);
        assertThat(view.firstGuess()).isEqualTo(30);
        assertThat(view.direction()).isEqualTo(Direction.OLDER);
        assertThat(view.gap()).isEqualTo(GapBand.CLEARLY);
        assertThat(view.range()).isEqualTo(new GuessRange(36, 45));
        assertThat(view.categories()).containsExactly(HintCategory.FILM);
        // A single category offered: the player picks it alone.
        assertThat(view.hintsToPick()).isEqualTo(1);
        assertThat(view.hints()).isNull();
        assertThat(view.result()).isNull();
    }

    @Test
    void asksForThreeCategoriesWhenMoreAreOffered() {
        when(hintRepository.findEligible(HINT_YEAR, HintPicker.audiences(Language.EN), QID))
                .thenReturn(hints(5, HintCategory.SPORT, HintCategory.MUSIC, HintCategory.WORLD, HintCategory.FILM));

        RoundView view = views.of(playableRound((short) 1), started(guest, (short) 1, 30));

        assertThat(view.categories())
                .containsExactly(HintCategory.MUSIC, HintCategory.FILM, HintCategory.SPORT, HintCategory.WORLD);
        assertThat(view.hintsToPick()).isEqualTo(3);
    }

    @Test
    void goesStraightToTheSecondGuessWhenNoCategoryCanBeOffered() {
        when(hintRepository.findEligible(anyInt(), any(), anyString())).thenReturn(hints(HintCategory.FILM, 4));

        RoundView view = views.of(playableRound((short) 1), started(guest, (short) 1, 50));

        assertThat(view.step()).isEqualTo(RoundStep.AWAITING_SECOND_GUESS);
        assertThat(view.direction()).isEqualTo(Direction.YOUNGER);
        assertThat(view.range()).isEqualTo(new GuessRange(35, 44));
        assertThat(view.categories()).isNull();
        assertThat(view.hints()).isEmpty();
    }

    @Test
    void showsTheHintsWithoutTheirYearWhileAwaitingTheSecondGuess() {
        Hint music = hints(HintCategory.MUSIC, 1).getFirst();
        Hint film = hints(HintCategory.FILM, 1).getFirst();
        when(hintRepository.findAllById(List.of(music.getId(), film.getId()))).thenReturn(List.of(music, film));
        GamePlay play = started(guest, (short) 1, 30);
        play.revealHints(List.of(HintCategory.MUSIC, HintCategory.FILM), List.of(music.getId(), film.getId()));

        RoundView view = views.of(playableRound((short) 1), play);

        assertThat(view.step()).isEqualTo(RoundStep.AWAITING_SECOND_GUESS);
        assertThat(view.hints())
                .containsExactly(
                        new RoundView.ShownHint(HintCategory.MUSIC, "Hint 0"),
                        new RoundView.ShownHint(HintCategory.FILM, "Hint 0"));
        assertThat(view.range()).isEqualTo(new GuessRange(36, 45));
        assertThat(view.categories()).isNull();
        assertThat(view.hintsToPick()).isNull();
    }

    @Test
    void revealsAFinishedRound() {
        Hint hint = hints(HintCategory.MUSIC, 1).getFirst();
        when(hintRepository.findAllById(List.of(hint.getId()))).thenReturn(List.of(hint));
        GamePlay play = started(guest, (short) 1, 30);
        play.revealHints(List.of(HintCategory.MUSIC), List.of(hint.getId()));
        play.finishWithSecondGuess(AGE, AGE, NOW);

        RoundView view = views.of(playableRound((short) 1), play);

        assertThat(view.step()).isEqualTo(RoundStep.FINISHED);
        assertThat(view.direction()).isNull();
        assertThat(view.range()).isNull();
        assertThat(view.result().age()).isEqualTo(AGE);
        assertThat(view.result().hints()).extracting(GameHint::year).containsExactly(HINT_YEAR);
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
        when(hintRepository.findEligible(HINT_YEAR, HintPicker.audiences(Language.FR), QID))
                .thenReturn(hints(HintCategory.SPORT, 6));

        assertThat(views.offer(playableRound((short) 1), Language.FR).categories())
                .isEqualTo(List.of(HintCategory.SPORT));
    }
}
