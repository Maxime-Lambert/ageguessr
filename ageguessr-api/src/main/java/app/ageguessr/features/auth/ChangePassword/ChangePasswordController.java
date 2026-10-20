package app.ageguessr.features.auth.ChangePassword;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ChangePasswordController {

    private final ChangePasswordHandler handler;

    @PostMapping("/api/auth/me/password")
    ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @CookieValue(value = "${app.refresh-token.cookie-name}", required = false) String rawRefreshToken,
            @Valid @RequestBody ChangePasswordCommand command) {
        handler.handle(UUID.fromString(jwt.getSubject()), rawRefreshToken, command);
        return ResponseEntity.noContent().build();
    }
}
