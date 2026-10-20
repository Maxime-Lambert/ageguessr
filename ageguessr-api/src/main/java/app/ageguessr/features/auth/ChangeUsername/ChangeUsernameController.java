package app.ageguessr.features.auth.ChangeUsername;

import app.ageguessr.features.auth.ProfileResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChangeUsernameController {

    private final ChangeUsernameHandler handler;

    @PatchMapping("/api/auth/me/username")
    ProfileResponse changeUsername(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangeUsernameCommand command) {
        return handler.handle(UUID.fromString(jwt.getSubject()), command);
    }
}
