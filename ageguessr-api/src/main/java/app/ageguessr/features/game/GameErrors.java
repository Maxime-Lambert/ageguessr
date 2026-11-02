package app.ageguessr.features.game;

import app.ageguessr.shared.exceptions.ConflictException;

/** The business refusals of a guess, all 409: the request is valid, the moment is not. */
public final class GameErrors {

    private GameErrors() {}

    /** Only today's game is played: a round started before midnight is closed after it. */
    public static ConflictException gameClosed() {
        return new ConflictException("GAME_CLOSED", "Only today's game can be played");
    }

    /** The photo was rejected or its person excluded since the draw: out for everyone. */
    public static ConflictException roundNeutralized() {
        return new ConflictException("ROUND_NEUTRALIZED", "This round was removed from today's game");
    }

    /** Already played, not yet allowed, or played at the same time from another tab. */
    public static ConflictException wrongStep() {
        return new ConflictException("WRONG_STEP", "This step of the round is not open");
    }

    public static ConflictException categoryUnavailable() {
        return new ConflictException("CATEGORY_UNAVAILABLE", "No hint of this category for this round");
    }
}
