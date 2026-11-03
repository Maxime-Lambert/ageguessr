package app.ageguessr.features.game.GetSeries;

import app.ageguessr.features.game.PlayerResolver;
import app.ageguessr.features.game.SeriesResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public, the JWT optional. Never sets the guest cookie: reading plays nothing. */
@RestController
@RequiredArgsConstructor
public class GetSeriesController {

    private final GetSeriesHandler handler;
    private final PlayerResolver players;

    @GetMapping("/api/game/series/{seriesId}")
    SeriesResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID seriesId, HttpServletRequest request) {
        return handler.handle(players.existing(jwt, request), seriesId);
    }
}
