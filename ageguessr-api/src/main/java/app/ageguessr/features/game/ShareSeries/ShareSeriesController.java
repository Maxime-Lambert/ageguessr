package app.ageguessr.features.game.ShareSeries;

import app.ageguessr.features.game.ChallengeResponse;
import app.ageguessr.features.game.PlayerResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public, the JWT optional; only the creator of the series gets past the handler. */
@RestController
@RequiredArgsConstructor
public class ShareSeriesController {

    private final ShareSeriesHandler handler;
    private final PlayerResolver players;

    @PostMapping("/api/game/series/{seriesId}/share")
    ChallengeResponse share(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID seriesId,
            @Valid @RequestBody ShareSeriesCommand command,
            HttpServletRequest request) {
        return handler.handle(players.existingOrThrow(jwt, request), seriesId, command);
    }
}
