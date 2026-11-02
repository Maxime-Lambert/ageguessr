package app.ageguessr.features.game.GetTodayGame;

import app.ageguessr.features.game.PlayerResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, the JWT optional (SecurityConfig). Never sets the guest cookie: reading plays nothing. */
@RestController
@RequiredArgsConstructor
class GetTodayGameController {

    private final GetTodayGameHandler handler;
    private final PlayerResolver players;

    @GetMapping("/api/game/today")
    TodayGameResponse today(@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return handler.handle(players.existing(jwt, request));
    }
}
