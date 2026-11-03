package app.ageguessr.features.game.SubmitSeriesFirstGuess;

import app.ageguessr.features.game.PlayerResolver;
import app.ageguessr.features.game.RoundView;
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

/**
 * Public, the JWT optional. No cookie is set here: a guest got one when creating or
 * joining the series, and without it is not one of its players.
 */
@RestController
@RequiredArgsConstructor
public class SubmitSeriesFirstGuessController {

    private final SubmitSeriesFirstGuessHandler handler;
    private final PlayerResolver players;

    @PostMapping("/api/game/series/{seriesId}/rounds/{position}/first-guess")
    RoundView firstGuess(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID seriesId,
            @PathVariable short position,
            @Valid @RequestBody SubmitSeriesFirstGuessCommand command,
            HttpServletRequest request) {
        return handler.handle(players.existingOrThrow(jwt, request), seriesId, position, command);
    }
}
