package app.ageguessr.features.game.ReportRound;

import app.ageguessr.features.game.PlayerResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, the JWT optional, as the rest of the game: a player reports a round they
 * finished, not a photo or hint id, which they never get. 204, whether the report is new
 * or was already sent from this round.
 */
@RestController
@RequiredArgsConstructor
public class ReportRoundController {

    private final ReportRoundHandler handler;
    private final PlayerResolver players;

    @PostMapping("/api/game/{date}/rounds/{position}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reportDaily(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @PathVariable short position,
            @Valid @RequestBody ReportRoundCommand command,
            HttpServletRequest request) {
        handler.handleDaily(players.existingOrThrow(jwt, request), date, position, command);
    }

    @PostMapping("/api/game/series/{seriesId}/rounds/{position}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reportSeries(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID seriesId,
            @PathVariable short position,
            @Valid @RequestBody ReportRoundCommand command,
            HttpServletRequest request) {
        handler.handleSeries(players.existingOrThrow(jwt, request), seriesId, position, command);
    }
}
