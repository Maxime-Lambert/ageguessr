package app.ageguessr.features.game.AbandonSeries;

import app.ageguessr.features.game.PlayerResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, as the rest of the free play: a guest is told apart by its cookie, whose
 * SameSite=Lax keeps a cross-site request from carrying it (GuestCookie).
 */
@RestController
@RequiredArgsConstructor
public class AbandonSeriesController {

    private final AbandonSeriesHandler handler;
    private final PlayerResolver players;

    @DeleteMapping("/api/game/series/{seriesId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void abandon(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID seriesId, HttpServletRequest request) {
        handler.handle(players.existingOrThrow(jwt, request), seriesId);
    }
}
