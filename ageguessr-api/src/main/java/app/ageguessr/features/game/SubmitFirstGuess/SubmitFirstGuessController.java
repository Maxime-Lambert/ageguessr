package app.ageguessr.features.game.SubmitFirstGuess;

import app.ageguessr.features.game.PlayerResolver;
import app.ageguessr.features.game.RoundView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

/** Public, the JWT optional; a guest without a cookie gets one here. */
@RestController
@RequiredArgsConstructor
public class SubmitFirstGuessController {

    private final SubmitFirstGuessHandler handler;
    private final PlayerResolver players;

    @PostMapping("/api/game/{date}/rounds/{position}/first-guess")
    RoundView firstGuess(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable short position,
            @Valid @RequestBody SubmitFirstGuessCommand command,
            HttpServletRequest request,
            HttpServletResponse response) {
        return handler.handle(players.playing(jwt, request, response), date, position, command);
    }
}
