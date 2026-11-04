package app.ageguessr.features.auth;

/**
 * Version of the Terms of Service a new account accepts, stored on the user at sign-up.
 * It is the "Last updated" date shown on the frontend /terms page: bump both together
 * whenever the Terms change significantly. Every account that accepted an older version
 * is then asked to accept this one (AcceptTerms), so a typo fix does not bump it.
 */
public final class TermsVersion {

    public static final String CURRENT = "2026-11-03";

    private TermsVersion() {}
}
