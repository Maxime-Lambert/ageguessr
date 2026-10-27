package app.ageguessr.shared.security;

/**
 * Custom claims of the access token. Shared so that the issuer (features/auth) and the
 * reader (config/JwtConfig) agree on names without config depending on a feature.
 */
public final class JwtClaims {

    /** Role names, e.g. ["ADMIN"]; absent for a regular user. */
    public static final String ROLES = "roles";

    private JwtClaims() {}
}
