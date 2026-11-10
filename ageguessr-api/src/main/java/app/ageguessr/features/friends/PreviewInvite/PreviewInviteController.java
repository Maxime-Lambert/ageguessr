package app.ageguessr.features.friends.PreviewInvite;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PreviewInviteController {

    private final PreviewInviteHandler handler;

    @GetMapping("/api/friends/invite/{code}")
    InvitePreviewResponse preview(@AuthenticationPrincipal Jwt jwt, @PathVariable String code) {
        return handler.handle(UUID.fromString(jwt.getSubject()), code);
    }
}
