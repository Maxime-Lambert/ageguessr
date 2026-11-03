package app.ageguessr.features.game;

import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.shared.i18n.Language;
import java.time.Instant;
import java.util.UUID;

/**
 * A player's round, daily or of a series: what the views, the reveal and the handlers
 * need, read from its {@link RoundProgress}. The defaults forward to it, so that each
 * entity only says where its progress is.
 */
public interface PlayedRound {

    RoundProgress progress();

    default Language getLanguage() {
        return progress().getLanguage();
    }

    default int getFirstGuess() {
        return progress().getFirstGuess();
    }

    default int getFirstPoints() {
        return progress().getFirstPoints();
    }

    default HintCategory getHintCategory() {
        return progress().getHintCategory();
    }

    default UUID getHintId() {
        return progress().getHintId();
    }

    default Integer getSecondGuess() {
        return progress().getSecondGuess();
    }

    default Integer getSecondPoints() {
        return progress().getSecondPoints();
    }

    default Instant getStartedAt() {
        return progress().getStartedAt();
    }

    default Instant getFinishedAt() {
        return progress().getFinishedAt();
    }

    default RoundStep step() {
        return progress().step();
    }

    default boolean acceptsSecondGuess(boolean anyCategoryAvailable) {
        return progress().acceptsSecondGuess(anyCategoryAvailable);
    }

    default void revealHint(HintCategory category, UUID hintId) {
        progress().revealHint(category, hintId);
    }

    default void finishWithSecondGuess(int guess, int age, Instant now) {
        progress().finishWithSecondGuess(guess, age, now);
    }

    default boolean firstGuessExact() {
        return progress().firstGuessExact();
    }

    default boolean isPhotoReported() {
        return progress().isPhotoReported();
    }

    default boolean isHintReported() {
        return progress().isHintReported();
    }

    default int points() {
        return progress().points();
    }
}
