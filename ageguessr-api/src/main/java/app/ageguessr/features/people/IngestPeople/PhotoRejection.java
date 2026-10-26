package app.ageguessr.features.people.IngestPeople;

/** Why a candidate photo was not kept. Counted per reason in the ingestion report. */
enum PhotoRejection {
    NOT_DATED,
    CONFLICTING_DATES,
    OUTSIDE_LIFETIME,
    BEFORE_PHOTOGRAPHY,
    AMBIGUOUS_AGE,
    AGE_OUT_OF_RANGE,
    FILE_NAME_YEAR_CONFLICT,
    NO_EXIF_DATE,
    EXIF_CONFLICT
}
