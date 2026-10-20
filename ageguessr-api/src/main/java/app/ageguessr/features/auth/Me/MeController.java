package app.ageguessr.features.auth.Me;

import app.ageguessr.features.auth.ProfileResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeController {

    private final MeHandler handler;

    @GetMapping("/api/auth/me")
    ProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return handler.handle(new MeQuery(UUID.fromString(jwt.getSubject())));
    }
}
