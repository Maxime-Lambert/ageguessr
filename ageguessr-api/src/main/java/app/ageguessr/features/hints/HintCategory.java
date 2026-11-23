package app.ageguessr.features.hints;

/** What a hint talks about. The player picks a few before the second guess. */
public enum HintCategory {
    MUSIC,
    FILM,
    SPORT,
    SCIENCE,
    WORLD,
    /** A well-known person born in the hint's year, never the person to guess. */
    CELEBRITIES,
    /** A work published (novel, play, poem, comic) or a literary prize awarded (V23). */
    LITERATURE,
    /** A programme first aired: a series, a game show, a talk show (V30). */
    TELEVISION,
    /** A video game released (V30): none before the seventies, those years offer it not. */
    VIDEO_GAMES
}
