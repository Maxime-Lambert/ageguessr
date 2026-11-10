package app.ageguessr.features.friends.RegenerateInvite;

import app.ageguessr.features.friends.InviteResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RegenerateInviteController {

    private final RegenerateInviteHandler handler;

    @PostMapping("/api/friends/invite")
    InviteResponse regenerate(@AuthenticationPrincipal Jwt jwt) {
        return handler.handle(UUID.fromString(jwt.getSubject()));
    }
}
