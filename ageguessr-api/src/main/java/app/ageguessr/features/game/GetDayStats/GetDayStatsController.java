package app.ageguessr.features.game.GetDayStats;

import app.ageguessr.features.game.PlayerResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public, the JWT optional: a guest who finished the day sees the stats too. */
@RestController
@RequiredArgsConstructor
public class GetDayStatsController {

    private final GetDayStatsHandler handler;
    private final PlayerResolver players;

    @GetMapping("/api/game/days/{date}/stats")
    DayStatsResponse stats(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return handler.handle(date, players.existing(jwt, request));
    }
}
