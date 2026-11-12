package app.ageguessr.features.game;

import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.shared.i18n.Language;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /**
     * The categories chosen, in the order of {@link HintCategory}; empty until then (V20).
     * PostgreSQL arrays: a list of at most six, written once and read with the round.
     */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "varchar(16)[]")
    @Builder.Default
    private List<HintCategory> hintCategories = new ArrayList<>();

    /**
     * The hints shown, one per category and in the same order, so that the player always
     * sees the same ones again. An id may be null: a hint deleted under the former
     * {@code ON DELETE SET NULL}.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "uuid[]")
    @Builder.Default
    private List<UUID> hintIds = new ArrayList<>();

    private Integer secondGuess;

    private Integer secondPoints;

    private Instant startedAt;

    private Instant finishedAt;

    /** The player reported the photo of this round: once per round (V16). */
    private boolean photoReported;

    /** The categories whose hint the player reported: once per hint (V20, was V16's flag). */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "varchar(16)[]")
    @Builder.Default
    private List<HintCategory> reportedHintCategories = new ArrayList<>();

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
        return hintCategories.isEmpty() ? RoundStep.AWAITING_HINT : RoundStep.AWAITING_SECOND_GUESS;
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

    /** @param hintIds one per category, in the same order */
    public void revealHints(List<HintCategory> categories, List<UUID> hintIds) {
        if (step() != RoundStep.AWAITING_HINT) {
            throw new IllegalStateException("The hints of a round are chosen once, after the first guess");
        }
        if (categories.isEmpty() || categories.size() != hintIds.size()) {
            throw new IllegalArgumentException("One hint per category, at least one");
        }
        this.hintCategories = new ArrayList<>(categories);
        this.hintIds = new ArrayList<>(hintIds);
    }

    /** The categories chosen and their hints, side by side. */
    public List<HintCategory> getHintCategories() {
        return List.copyOf(hintCategories);
    }

    public List<UUID> getHintIds() {
        return Collections.unmodifiableList(hintIds);
    }

    public List<HintCategory> getReportedHintCategories() {
        return List.copyOf(reportedHintCategories);
    }

    /** The hint shown for this category, if the player chose it. */
    public Optional<UUID> hintIdOf(HintCategory category) {
        int index = hintCategories.indexOf(category);
        return index < 0 ? Optional.empty() : Optional.ofNullable(hintIds.get(index));
    }

    public void finishWithSecondGuess(int guess, int age, Instant now) {
        if (step() == RoundStep.FINISHED) {
            throw new IllegalStateException("The round is already finished");
        }
        this.secondGuess = guess;
        this.secondPoints = Scoring.points(guess, age);
        this.finishedAt = now;
    }

    /**
     * Marks the photo as reported by this player, from the result screen only: the
     * identity is known, and nothing about the answer is given away by a report form.
     *
     * @return false when it already was, so that a second report adds nothing
     */
    public boolean reportPhoto() {
        requireFinished();
        if (photoReported) {
            return false;
        }
        photoReported = true;
        return true;
    }

    /** As {@link #reportPhoto()}, for the hint of one category shown during the round. */
    public boolean reportHint(HintCategory category) {
        requireFinished();
        if (!hintCategories.contains(category)) {
            throw new IllegalArgumentException("Only a hint shown is reported");
        }
        if (reportedHintCategories.contains(category)) {
            return false;
        }
        // A new list rather than add(): the dirty check of a basic array compares values, and
        // the list read from the database may not be mutable.
        List<HintCategory> reported = new ArrayList<>(reportedHintCategories);
        reported.add(category);
        reportedHintCategories = reported;
        return true;
    }

    private void requireFinished() {
        if (step() != RoundStep.FINISHED) {
            throw new IllegalStateException("A round is reported once finished");
        }
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
