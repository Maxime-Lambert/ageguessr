package app.ageguessr.features.auth;

/**
 * Internal handler result — never serialized directly. The access token goes in the
 * JSON response body, the raw refresh token is only ever used by the controller to set
 * the HttpOnly cookie via RefreshTokenCookieFactory, never returned in a response body.
 */
public record TokenPair(String accessToken, String rawRefreshToken) {}
