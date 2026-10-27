package app.ageguessr.features.people;

/**
 * Gender as Wikidata states it (P21), used only to balance the pool between men and
 * women. Trans women count as women and trans men as men.
 */
public enum Gender {
    MALE,
    FEMALE,
    /** Any other value (non-binary, intersex...), or several contradictory values. */
    OTHER
}
