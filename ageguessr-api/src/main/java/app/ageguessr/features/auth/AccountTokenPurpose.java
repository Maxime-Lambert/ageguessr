package app.ageguessr.features.auth;

/** What an AccountToken authorizes. Stored as its name; a token is only ever accepted for its own purpose. */
public enum AccountTokenPurpose {
    PASSWORD_RESET,
    ACCOUNT_DELETION,
    /** The password is right, the two-factor code is still to come (5 minutes). */
    TOTP_LOGIN,
    /** The emailed link that turns two-factor off, when the app is lost (1 hour). */
    TOTP_RECOVERY
}
