package app.ageguessr.features.game;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The random id of a player without an account. Strictly necessary to play (it ties the
 * guesses of a round together), so no consent is needed; listed on /privacy.
 *
 * <ul>
 *   <li>httpOnly: no script reads it, so an XSS cannot carry it away;
 *   <li>SameSite=Lax: a cross-site POST does not carry it, which closes CSRF on the
 *       guesses without a token, as for the refresh cookie (see SecurityConfig);
 *   <li>Path=/api/game: never sent with the other API calls.
 * </ul>
 */
@Component
public class GuestCookie {

    public static final String NAME = "ageguessr_guest";

    private final Duration ttl;
    private final boolean secure;

    public GuestCookie(GuestProperties properties, @Value("${app.cookie.secure}") boolean secure) {
        this.ttl = Duration.ofDays(properties.cookieTtlDays());
        this.secure = secure;
    }

    /** The guest id sent, if any. A value that is not a UUID is ignored, as if absent. */
    public Optional<UUID> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()))
                .findFirst()
                .flatMap(cookie -> parse(cookie.getValue()));
    }

    public ResponseCookie create(UUID guestId) {
        return ResponseCookie.from(NAME, guestId.toString())
                .httpOnly(true)
                .secure(secure)
                .path("/api/game")
                .sameSite("Lax")
                .maxAge(ttl)
                .build();
    }

    private static Optional<UUID> parse(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
