package app.ageguessr.features.game;

import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.shared.i18n.Language;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * How far a player is in one round, whatever the game: the daily game ({@link GamePlay})
 * or a free series ({@link FreeSeriesPlay}). The same rules, so they live once, embedded in
 * both tables with the same columns. The step is read from the columns rather than
 * stored, so that it can never disagree with them. The methods refuse a move out of
 * turn; the handlers check the step first and answer a business error, these checks
 * being the last line.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class RoundProgress {

    @Enumerated(EnumType.STRING)
    private Language language;

    private int firstGuess;

    private int firstPoints;

    @Enumerated(EnumType.STRING)
    private HintCategory hintCategory;

    /** The hint shown, so that the player always sees the same one again. */
    private UUID hintId;

    private Integer secondGuess;

    private Integer secondPoints;

    private Instant startedAt;

    private Instant finishedAt;

    /** Starts the round with its first guess; an exact one ends it at once. */
    public static RoundProgress start(Language language, int guess, int age, Instant now) {
        return RoundProgress.builder()
                .language(language)
                .firstGuess(guess)
                .firstPoints(Scoring.points(guess, age))
                .startedAt(now)
                .finishedAt(guess == age ? now : null)
                .build();
    }

    public RoundStep step() {
        if (finishedAt != null) {
            return RoundStep.FINISHED;
        }
        return hintCategory == null ? RoundStep.AWAITING_HINT : RoundStep.AWAITING_SECOND_GUESS;
    }

    /**
     * The hint is required before the second guess, unless no category could be offered:
     * the player then goes straight to the second guess. Read at each request rather than
     * stored, so a category approved in the meantime is offered.
     */
    public boolean acceptsSecondGuess(boolean anyCategoryAvailable) {
        RoundStep step = step();
        return step == RoundStep.AWAITING_SECOND_GUESS || (step == RoundStep.AWAITING_HINT && !anyCategoryAvailable);
    }

    public void revealHint(HintCategory category, UUID hintId) {
        if (step() != RoundStep.AWAITING_HINT) {
            throw new IllegalStateException("The hint of a round is chosen once, after the first guess");
        }
        this.hintCategory = category;
        this.hintId = hintId;
    }

    public void finishWithSecondGuess(int guess, int age, Instant now) {
        if (step() == RoundStep.FINISHED) {
            throw new IllegalStateException("The round is already finished");
        }
        this.secondGuess = guess;
        this.secondPoints = Scoring.points(guess, age);
        this.finishedAt = now;
    }

    /** Ended by the first guess: exact, so there was no hint and no second guess. */
    public boolean firstGuessExact() {
        return finishedAt != null && secondGuess == null;
    }

    /** Out of {@value Scoring#EXACT_FIRST_GUESS_ROUND}; only once the round is finished. */
    public int points() {
        if (step() != RoundStep.FINISHED) {
            throw new IllegalStateException("A round has points once finished");
        }
        return firstGuessExact() ? Scoring.EXACT_FIRST_GUESS_ROUND : firstPoints + secondPoints;
    }
}
