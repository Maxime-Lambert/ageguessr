package app.ageguessr.features.game.SubmitSeriesSecondGuess;

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

@RestController
@RequiredArgsConstructor
public class SubmitSeriesSecondGuessController {

    private final SubmitSeriesSecondGuessHandler handler;
    private final PlayerResolver players;

    @PostMapping("/api/game/series/{seriesId}/rounds/{position}/second-guess")
    RoundView secondGuess(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID seriesId,
            @PathVariable short position,
            @Valid @RequestBody SubmitSeriesSecondGuessCommand command,
            HttpServletRequest request) {
        return handler.handle(players.existingOrThrow(jwt, request), seriesId, position, command);
    }
}
