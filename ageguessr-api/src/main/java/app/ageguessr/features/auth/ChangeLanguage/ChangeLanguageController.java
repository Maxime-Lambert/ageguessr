package app.ageguessr.features.auth.ChangeLanguage;

import app.ageguessr.features.auth.ProfileResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * PUT rather than PATCH like the username: the language is set whole, and setting it twice
 * changes nothing (the front sends it again whenever the site language changes).
 */
@RestController
@RequiredArgsConstructor
public class ChangeLanguageController {

    private final ChangeLanguageHandler handler;

    @PutMapping("/api/auth/me/language")
    ProfileResponse changeLanguage(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangeLanguageCommand command) {
        return handler.handle(UUID.fromString(jwt.getSubject()), command);
    }
}
