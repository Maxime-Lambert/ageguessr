package app.ageguessr.features.game;

/**
 * Where a player is in a round. NOT_STARTED has no game_plays row; an exact first guess
 * goes straight to FINISHED. The hint is required before the second guess, unless no
 * category has enough hints for the round (see {@link GamePlay#acceptsSecondGuess}).
 */
public enum RoundStep {
    NOT_STARTED,
    AWAITING_HINT,
    AWAITING_SECOND_GUESS,
    FINISHED
}
