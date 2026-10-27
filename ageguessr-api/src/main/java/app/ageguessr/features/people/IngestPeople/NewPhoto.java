package app.ageguessr.features.people.IngestPeople;

/** A selected photo with its re-encoded image, ready to store. {@code commonsFile} has no "File:" prefix. */
record NewPhoto(
        String commonsFile,
        PhotoEvaluation.Accepted evaluation,
        String licenseShortName,
        String licenseUrl,
        String authorText,
        String sourceUrl,
        ImageReencoder.Reencoded image) {}
