package app.ageguessr.features.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Path is scoped to /api/auth (narrower than "/") — the refresh cookie is only ever
 * needed by the refresh and logout endpoints, so it's never sent on unrelated API
 * calls once the game endpoints exist. Domain is intentionally left unset: it defaults
 * to the API's own host, which is exactly what's wanted since only the backend itself
 * ever reads this cookie.
 */
@Component
public class RefreshTokenCookieFactory {

    private final String cookieName;
    private final int ttlDays;
    private final boolean secure;

    public RefreshTokenCookieFactory(
            @Value("${app.refresh-token.cookie-name}") String cookieName,
            @Value("${app.refresh-token.ttl-days}") int ttlDays,
            @Value("${app.cookie.secure}") boolean secure) {
        this.cookieName = cookieName;
        this.ttlDays = ttlDays;
        this.secure = secure;
    }

    public String cookieName() {
        return cookieName;
    }

    public ResponseCookie create(String rawToken) {
        return baseCookie(rawToken).maxAge(Duration.ofDays(ttlDays)).build();
    }

    public ResponseCookie clear() {
        return baseCookie("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(true)
                .secure(secure)
                .path("/api/auth")
                .sameSite("Lax");
    }
}
