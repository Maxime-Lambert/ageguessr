package app.ageguessr.features.hints;

/** What a hint talks about. The player picks one before the second guess. */
public enum HintCategory {
    MUSIC,
    FILM,
    SPORT,
    SCIENCE,
    WORLD,
    /** A well-known person born in the hint's year, never the person to guess. */
    CELEBRITIES
}
