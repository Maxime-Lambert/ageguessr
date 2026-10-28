package app.ageguessr.features.people.IngestPeople;

/**
 * What the Commons imageinfo API says about one file. {@code title} keeps its "File:"
 * prefix. {@code artistHtml} is raw HTML from Commons, to clean before storing.
 * {@code thumbnailHeight}: height of the thumbnail behind {@code thumbnailUrl}, 0 if
 * Commons did not say.
 */
record CommonsFile(
        String title,
        long pageId,
        String mime,
        String thumbnailUrl,
        String descriptionUrl,
        String informationDate,
        String exifDateTimeOriginal,
        String licenseShortName,
        String licenseUrl,
        String artistHtml,
        int thumbnailHeight) {}
