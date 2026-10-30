package app.ageguessr.features.hints.ImportHints;

/** Why a line of a hint file is refused. */
enum HintRejection {
    MISSING_KEY,
    KEY_TOO_LONG,
    DUPLICATE_KEY,
    UNKNOWN_CATEGORY,
    UNKNOWN_AUDIENCE,
    YEAR_OUT_OF_RANGE,
    TEXTS_DO_NOT_MATCH_AUDIENCE,
    TEXT_TOO_LONG,
    YEAR_IN_TEXT,
    INVALID_SOURCE,
    INVALID_SUBJECT
}
