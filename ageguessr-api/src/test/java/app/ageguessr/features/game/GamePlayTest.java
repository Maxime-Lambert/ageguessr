package app.ageguessr.features.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.features.hints.HintCategory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GamePlayTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    private static final Instant START = Instant.parse("2026-11-02T08:00:00Z");
    private static final Instant END = Instant.parse("2026-11-02T08:01:00Z");
    private static final int AGE = 40;

    @Test
    void awaitsAHintAfterAWrongFirstGuessAndKeepsItsPoints() {
        GamePlay play = start(30);

        assertThat(play.step()).isEqualTo(RoundStep.AWAITING_HINT);
        assertThat(play.getFirstPoints()).isEqualTo(Scoring.points(30, AGE));
        assertThat(play.getFinishedAt()).isNull();
    }

    @Test
    void endsTheRoundAtOnceOnAnExactFirstGuess() {
        GamePlay play = start(AGE);

        assertThat(play.step()).isEqualTo(RoundStep.FINISHED);
        assertThat(play.firstGuessExact()).isTrue();
        assertThat(play.getFinishedAt()).isEqualTo(START);
        assertThat(play.points()).isEqualTo(1000);
    }

    @Test
    void goesThroughTheHintThenTheSecondGuess() {
        GamePlay play = start(30);
        UUID hintId = UUID.randomUUID();

        play.revealHint(HintCategory.MUSIC, hintId);
        assertThat(play.step()).isEqualTo(RoundStep.AWAITING_SECOND_GUESS);
        assertThat(play.getHintId()).isEqualTo(hintId);

        play.secondGuess(38, AGE, END);
        assertThat(play.step()).isEqualTo(RoundStep.FINISHED);
        assertThat(play.firstGuessExact()).isFalse();
        assertThat(play.getSecondPoints()).isEqualTo(Scoring.points(38, AGE));
        assertThat(play.getFinishedAt()).isEqualTo(END);
        assertThat(play.points()).isEqualTo(Scoring.points(30, AGE) + Scoring.points(38, AGE));
    }

    @Test
    void anExactSecondGuessIsWorthItsFiveHundredPointsOnly() {
        GamePlay play = start(30);
        play.revealHint(HintCategory.MUSIC, UUID.randomUUID());

        play.secondGuess(AGE, AGE, END);

        assertThat(play.points()).isEqualTo(Scoring.points(30, AGE) + 500);
    }

    @Test
    void acceptsTheSecondGuessWithoutAHintOnlyWhenNoCategoryIsAvailable() {
        GamePlay play = start(30);

        assertThat(play.acceptsSecondGuess(true)).isFalse();
        assertThat(play.acceptsSecondGuess(false)).isTrue();

        play.revealHint(HintCategory.MUSIC, UUID.randomUUID());
        assertThat(play.acceptsSecondGuess(true)).isTrue();
    }

    @Test
    void acceptsNoSecondGuessOnceFinished() {
        GamePlay play = start(AGE);

        assertThat(play.acceptsSecondGuess(false)).isFalse();
        assertThatThrownBy(() -> play.secondGuess(30, AGE, END)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesASecondHint() {
        GamePlay play = start(30);
        play.revealHint(HintCategory.MUSIC, UUID.randomUUID());

        assertThatThrownBy(() -> play.revealHint(HintCategory.FILM, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesAHintAfterAnExactFirstGuess() {
        GamePlay play = start(AGE);

        assertThatThrownBy(() -> play.revealHint(HintCategory.MUSIC, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hasNoPointsBeforeTheEnd() {
        GamePlay play = start(30);

        assertThatThrownBy(play::points).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void belongsToTheAccountOrTheGuestGiven() {
        UUID userId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();

        GamePlay ofAccount = GamePlay.start(DAY, (short) 1, PlayerKey.account(userId), Language.EN, 30, AGE, START);
        GamePlay ofGuest = GamePlay.start(DAY, (short) 2, PlayerKey.guest(guestId), Language.FR, 30, AGE, START);

        assertThat(ofAccount.getUserId()).isEqualTo(userId);
        assertThat(ofAccount.getGuestId()).isNull();
        assertThat(ofGuest.getGuestId()).isEqualTo(guestId);
        assertThat(ofGuest.getUserId()).isNull();
        assertThat(ofGuest.getPosition()).isEqualTo((short) 2);
        assertThat(ofGuest.getLanguage()).isEqualTo(Language.FR);
    }

    @Test
    void aPlayerIsEitherAnAccountOrAGuest() {
        assertThatThrownBy(() -> new PlayerKey(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlayerKey(null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    private static GamePlay start(int guess) {
        return GamePlay.start(DAY, (short) 1, PlayerKey.guest(UUID.randomUUID()), Language.EN, guess, AGE, START);
    }
}
