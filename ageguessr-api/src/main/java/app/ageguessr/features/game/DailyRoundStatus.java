package app.ageguessr.features.game;

/**
 * Whether a round of the day can still be played. Read from the photo at each request
 * rather than stored, so that the people feature never has to tell the game about a
 * curation decision, and an undone rejection restores the round.
 */
public enum DailyRoundStatus {
    PLAYABLE,
    /**
     * The photo was rejected by an admin, or deleted with its excluded person: the round is
     * left out of the day's score for every player (user decision, 2026-11-02). A player
     * report alone never neutralizes a round, or a script could empty the game.
     */
    NEUTRALIZED
}
