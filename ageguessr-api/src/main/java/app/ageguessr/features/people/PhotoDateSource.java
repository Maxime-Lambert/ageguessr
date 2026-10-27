package app.ageguessr.features.people;

/** Where the declared shooting date of a photo came from. Always corroborated by the camera EXIF. */
public enum PhotoDateSource {
    /** Structured Data on Commons, "inception" (P571). */
    STRUCTURED_DATA,
    /** The "date" field of the {{Information}} template. */
    INFORMATION_TEMPLATE,
    /** Both were present and overlapped; the stored interval is their intersection. */
    BOTH
}
