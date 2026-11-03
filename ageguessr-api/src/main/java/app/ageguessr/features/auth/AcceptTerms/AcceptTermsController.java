package app.ageguessr.features.auth.AcceptTerms;

import app.ageguessr.features.auth.ProfileResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Authenticated. PUT: accepting the same version twice changes nothing. */
@RestController
@RequiredArgsConstructor
public class AcceptTermsController {

    private final AcceptTermsHandler handler;

    @PutMapping("/api/auth/me/terms")
    ProfileResponse accept(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AcceptTermsCommand command) {
        return handler.handle(UUID.fromString(jwt.getSubject()), command);
    }
}
