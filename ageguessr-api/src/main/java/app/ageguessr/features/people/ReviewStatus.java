package app.ageguessr.features.people;

/**
 * Manual curation of a photo. Only APPROVED photos reach the game: automatic rules cannot
 * tell a portrait from a crowd shot where the person happens to be the one tagged.
 */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    /** Never proposed again, and frees its year and age for another photo of the person. */
    REJECTED
}
