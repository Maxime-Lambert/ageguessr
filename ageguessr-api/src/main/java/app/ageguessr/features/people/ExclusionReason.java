package app.ageguessr.features.people;

/** Why a person is excluded (people.exclusion_reason, V10). */
public enum ExclusionReason {
    /** From the curation page: a removal request, or the editorial line applied by hand. */
    MANUAL,
    /** Caught by an automatic criterion of the editorial line (ingestion, --apply-editorial-line). */
    EDITORIAL_LINE
}
