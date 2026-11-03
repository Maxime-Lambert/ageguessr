package app.ageguessr.features.game;

/**
 * Where a player is in a round. NOT_STARTED has no row yet (game_plays,
 * free_series_plays); an exact first guess goes straight to FINISHED. The hint is required before the second guess, unless no
 * category has enough hints for the round (see {@link RoundProgress#acceptsSecondGuess}).
 */
public enum RoundStep {
    NOT_STARTED,
    AWAITING_HINT,
    AWAITING_SECOND_GUESS,
    FINISHED
}
