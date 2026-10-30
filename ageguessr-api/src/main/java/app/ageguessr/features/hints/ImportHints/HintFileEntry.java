package app.ageguessr.features.hints.ImportHints;

/**
 * One line of a file of src/main/resources/hints. Category and audience stay strings so
 * that a bad value refuses its line ({@link HintRules}) instead of failing the whole file.
 */
record HintFileEntry(
        String category,
        Integer year,
        String audience,
        String textFr,
        String textEn,
        String sourceUrl,
        String sourceKind,
        String subjectQid,
        String dedupeKey,
        boolean translated) {}
