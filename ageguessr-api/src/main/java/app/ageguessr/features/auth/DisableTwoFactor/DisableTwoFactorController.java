package app.ageguessr.features.auth.DisableTwoFactor;

import app.ageguessr.features.auth.ProfileResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DisableTwoFactorController {

    private final DisableTwoFactorHandler handler;

    @PostMapping("/api/auth/me/two-factor/disable")
    ProfileResponse disable(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DisableTwoFactorCommand command) {
        return handler.handle(UUID.fromString(jwt.getSubject()), command);
    }
}
