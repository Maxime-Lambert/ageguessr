package app.ageguessr.features.game;

import app.ageguessr.features.hints.HintCategory;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One player's round of a day (V13), created by the first guess. The step is read from
 * the columns rather than stored, so that it can never disagree with them. The methods
 * refuse a move out of turn; the handlers check the step first and answer a business
 * error, these checks being the last line.
 */
@Entity
@Table(name = "game_plays")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class GamePlay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private LocalDate gameDate;

    private short position;

    private UUID userId;

    private UUID guestId;

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

    /** Two tabs playing the same step at once: the second write fails instead of overwriting. */
    @Version
    private int version;

    /** Starts the round with its first guess; an exact one ends it at once. */
    public static GamePlay start(
            LocalDate gameDate, short position, PlayerKey player, Language language, int guess, int age, Instant now) {
        return GamePlay.builder()
                .gameDate(gameDate)
                .position(position)
                .userId(player.userId())
                .guestId(player.guestId())
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

    public void secondGuess(int guess, int age, Instant now) {
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
