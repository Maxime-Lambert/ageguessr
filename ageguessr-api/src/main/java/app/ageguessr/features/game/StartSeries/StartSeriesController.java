package app.ageguessr.features.game.StartSeries;

import app.ageguessr.features.game.PlayerResolver;
import app.ageguessr.features.game.SeriesResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public, the JWT optional; a guest without a cookie gets one here. Rate limited (RateLimitFilter). */
@RestController
@RequiredArgsConstructor
public class StartSeriesController {

    private final StartSeriesHandler handler;
    private final PlayerResolver players;

    @PostMapping("/api/game/series")
    SeriesResponse start(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody StartSeriesCommand command,
            HttpServletRequest request,
            HttpServletResponse response) {
        return handler.handle(players.playing(jwt, request, response), command);
    }
}
