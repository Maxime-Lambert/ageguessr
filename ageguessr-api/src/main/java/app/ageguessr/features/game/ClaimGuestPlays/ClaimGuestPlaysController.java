package app.ageguessr.features.game.ClaimGuestPlays;

import app.ageguessr.features.game.GuestCookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by the front right after a login. Under /api/game because the guest cookie is
 * only sent there (Path=/api/game): the login request never carries it. Authenticated
 * (SecurityConfig). The guest cookie is removed, so a later game without logging in
 * starts a new guest.
 */
@RestController
@RequiredArgsConstructor
public class ClaimGuestPlaysController {

    private final ClaimGuestPlaysHandler handler;
    private final GuestCookie guestCookie;

    @PostMapping("/api/game/guest-plays/claim")
    ResponseEntity<ClaimGuestPlaysResponse> claim(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return guestCookie
                .read(request)
                .map(guestId -> ResponseEntity.ok()
                        .header(HttpHeaders.SET_COOKIE, guestCookie.clear().toString())
                        .body(new ClaimGuestPlaysResponse(handler.handle(guestId, UUID.fromString(jwt.getSubject())))))
                .orElseGet(() -> ResponseEntity.ok(new ClaimGuestPlaysResponse(0)));
    }
}
