package app.ageguessr.features.auth.Logout;

/** rawToken may be null - logging out with no refresh cookie present is a no-op, not an error. */
public record LogoutCommand(String rawToken) {}
