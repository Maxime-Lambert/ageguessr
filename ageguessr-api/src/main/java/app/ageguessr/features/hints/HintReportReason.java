package app.ageguessr.features.hints;

/** Why a player reports a hint (V16): a closed list, no free text to moderate. */
public enum HintReportReason {
    WRONG_YEAR,
    FALSE,
    UNCLEAR,
    DISTURBING,
    OTHER
}
