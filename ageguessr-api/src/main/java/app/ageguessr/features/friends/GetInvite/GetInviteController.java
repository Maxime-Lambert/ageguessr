package app.ageguessr.features.friends.GetInvite;

import app.ageguessr.features.friends.InviteResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GetInviteController {

    private final GetInviteHandler handler;

    @GetMapping("/api/friends/invite")
    InviteResponse get(@AuthenticationPrincipal Jwt jwt) {
        return handler.handle(UUID.fromString(jwt.getSubject()));
    }
}
