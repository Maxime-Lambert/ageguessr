package app.ageguessr.shared.reports;

/**
 * Where a player's report stands (V16), the same for a photo and for a hint, so that
 * both features share it without depending on each other.
 */
public enum ReportStatus {
    /** Waiting for the admin. */
    OPEN,
    /** The admin made a decision on the target: rejected, corrected, approved again. */
    RESOLVED,
    /** The admin kept the target as it is. */
    DISMISSED
}
