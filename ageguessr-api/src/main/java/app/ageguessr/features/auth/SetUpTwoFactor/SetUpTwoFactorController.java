package app.ageguessr.features.auth.SetUpTwoFactor;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SetUpTwoFactorController {

    private final SetUpTwoFactorHandler handler;

    @PostMapping("/api/auth/me/two-factor/setup")
    ResponseEntity<SetUpTwoFactorResponse> setUp(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SetUpTwoFactorCommand command) {
        // The secret itself: no cache keeps it.
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(handler.handle(UUID.fromString(jwt.getSubject()), command));
    }
}
