package app.ageguessr.features.game.SubmitSecondGuess;

import app.ageguessr.features.game.PlayerResolver;
import app.ageguessr.features.game.RoundView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public, the JWT optional. A guest without a cookie is refused, see PlayerResolver.existingOrThrow. */
@RestController
@RequiredArgsConstructor
public class SubmitSecondGuessController {

    private final SubmitSecondGuessHandler handler;
    private final PlayerResolver players;

    @PostMapping("/api/game/{date}/rounds/{position}/second-guess")
    RoundView secondGuess(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable short position,
            @Valid @RequestBody SubmitSecondGuessCommand command,
            HttpServletRequest request) {
        return handler.handle(players.existingOrThrow(jwt, request), date, position, command);
    }
}
