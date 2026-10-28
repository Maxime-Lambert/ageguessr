package app.ageguessr.features.people.IngestPeople;

/** Why a candidate photo was not kept. Counted per reason in the ingestion report. */
enum PhotoRejection {
    UNSUPPORTED_FORMAT,
    LICENSE_NOT_ALLOWED,
    GROUP_PHOTO,
    NOT_DATED,
    CONFLICTING_DATES,
    OUTSIDE_LIFETIME,
    BEFORE_PHOTOGRAPHY,
    AMBIGUOUS_AGE,
    MINOR,
    AGE_OUT_OF_RANGE,
    FILE_NAME_YEAR_CONFLICT,
    NO_EXIF_DATE,
    EXIF_CONFLICT,
    /** The thumbnail is under app.people.ingest.min-image-height: the original is too small. */
    TOO_SMALL,
    /** Valid, but outside the age bands the run aims at (--age-bands). */
    AGE_BAND_NOT_WANTED
}
