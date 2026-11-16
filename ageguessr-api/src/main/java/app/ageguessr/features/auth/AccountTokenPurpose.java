package app.ageguessr.features.auth;

/** What an AccountToken authorizes. Stored as its name; a token is only ever accepted for its own purpose. */
public enum AccountTokenPurpose {
    PASSWORD_RESET,
    ACCOUNT_DELETION
}
