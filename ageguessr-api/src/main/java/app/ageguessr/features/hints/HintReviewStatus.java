package app.ageguessr.features.hints;

/**
 * Whether a hint can be drawn. Hints taken from a page in their own language are
 * APPROVED from the start, only translated ones wait for an admin: reading them all would
 * take too long, and players can report a wrong one.
 */
public enum HintReviewStatus {
    PENDING,
    APPROVED,
    /** Never drawn. Possible at any time, including for a hint already APPROVED. */
    REJECTED
}
