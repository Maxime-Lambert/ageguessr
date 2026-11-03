package app.ageguessr.features.game.GetChallenge;

import app.ageguessr.features.game.ChallengeResponse;
import app.ageguessr.features.game.PlayerResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Public, the JWT optional. Never sets the guest cookie: reading plays nothing. */
@RestController
@RequiredArgsConstructor
public class GetChallengeController {

    private final GetChallengeHandler handler;
    private final PlayerResolver players;

    @GetMapping("/api/game/challenges/{code}")
    ChallengeResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable String code, HttpServletRequest request) {
        return handler.handle(players.existing(jwt, request), code);
    }
}
