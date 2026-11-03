package app.ageguessr.features.people;

/** Why a player reports a photo (V16): a closed list, no free text to moderate. */
public enum PhotoReportReason {
    TOO_SMALL,
    NOT_IDENTIFIABLE,
    WRONG_PERSON,
    /** Urgent: no minor may be on a photo (V8), the admin is told at the first report. */
    LOOKS_MINOR,
    /** The editorial line rather than the photo: leads to an exclusion more than a rejection. */
    DISTURBING_PERSON,
    OTHER
}
