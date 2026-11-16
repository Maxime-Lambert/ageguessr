package app.ageguessr.features.game.GetCalendar;

import app.ageguessr.features.game.PlayerResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.YearMonth;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, the JWT optional (SecurityConfig): a guest's days come from the guest cookie,
 * sent only under /api/game. Never sets the cookie: reading plays nothing.
 */
@RestController
@RequiredArgsConstructor
public class GetCalendarController {

    private final GetCalendarHandler handler;
    private final PlayerResolver players;

    @GetMapping("/api/game/calendar")
    CalendarResponse calendar(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return handler.handle(Optional.ofNullable(month), players.existing(jwt, request));
    }
}
