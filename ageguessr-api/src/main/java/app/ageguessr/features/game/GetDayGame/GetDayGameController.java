package app.ageguessr.features.game.GetDayGame;

import app.ageguessr.features.game.DayGameResponse;
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

/** Public, the JWT optional (SecurityConfig). Never sets the guest cookie: reading plays nothing. */
@RestController
@RequiredArgsConstructor
public class GetDayGameController {

    private final GetDayGameHandler handler;
    private final PlayerResolver players;

    @GetMapping("/api/game/days/{date}")
    DayGameResponse day(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return handler.handle(date, players.existing(jwt, request));
    }
}
