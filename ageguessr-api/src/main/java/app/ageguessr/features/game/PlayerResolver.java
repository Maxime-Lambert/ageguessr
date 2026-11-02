package app.ageguessr.features.game;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Who sends a game request. The JWT is optional on /api/game/** (SecurityConfig): a
 * valid one makes the player the account, even with a guest cookie; without one, the
 * guest cookie does.
 */
@Component
@RequiredArgsConstructor
public class PlayerResolver {

    private final GuestCookie guestCookie;

    /** Reading only: a guest without a cookie has played nothing, and gets no cookie. */
    public Optional<PlayerKey> existing(Jwt jwt, HttpServletRequest request) {
        if (jwt != null) {
            return Optional.of(PlayerKey.account(UUID.fromString(jwt.getSubject())));
        }
        return guestCookie.read(request).map(PlayerKey::guest);
    }

    /**
     * A later step of a round (hint, second guess): a guest without a cookie has no round
     * started, so the step is refused, and no cookie is set.
     */
    public PlayerKey existingOrThrow(Jwt jwt, HttpServletRequest request) {
        return existing(jwt, request).orElseThrow(GameErrors::wrongStep);
    }

    /**
     * Playing: a guest without a cookie gets a new random id, set on the response. The id
     * comes from UUID.randomUUID (SecureRandom), so it cannot be guessed to play in
     * someone else's place.
     */
    public PlayerKey playing(Jwt jwt, HttpServletRequest request, HttpServletResponse response) {
        return existing(jwt, request).orElseGet(() -> {
            UUID guestId = UUID.randomUUID();
            response.addHeader(HttpHeaders.SET_COOKIE, guestCookie.create(guestId).toString());
            return PlayerKey.guest(guestId);
        });
    }
}
