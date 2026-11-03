package app.ageguessr.features.game.GetCurrentSeries;

import app.ageguessr.features.game.PlayerResolver;
import app.ageguessr.features.game.SeriesResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, the JWT optional. 204 when there is nothing to pick up. */
@RestController
@RequiredArgsConstructor
public class GetCurrentSeriesController {

    private final GetCurrentSeriesHandler handler;
    private final PlayerResolver players;

    @GetMapping("/api/game/series/current")
    ResponseEntity<SeriesResponse> current(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return handler.handle(players.existing(jwt, request))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
