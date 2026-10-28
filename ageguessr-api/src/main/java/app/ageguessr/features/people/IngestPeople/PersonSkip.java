package app.ageguessr.features.people.IngestPeople;

/** Why a person from the Wikidata list ends up without any photo in the game. Counted in the ingestion report. */
enum PersonSkip {
    /** Removed on request (people.excluded): never re-ingested. */
    EXCLUDED,
    /** Caught by a criterion of the editorial line (EditorialLine): never ingested. */
    EDITORIAL_LINE,
    BIRTH_DATE_UNUSABLE,
    DEATH_DATE_UNUSABLE,
    NO_NAME,
    /**
     * Wikidata now gives another birth date than the one stored: the ages of the photos
     * already stored would be wrong. Left for a human to check rather than mixed.
     */
    BIRTH_DATE_CHANGED,
    NO_VALID_PHOTO,
    /** A Wikimedia service kept failing for this person. Retried on the next run. */
    FAILED
}
