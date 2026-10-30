package app.ageguessr.features.hints.ReviewHint;

import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Admin only (SecurityConfig). PUT: sending the same decision twice changes nothing. */
@RestController
@RequiredArgsConstructor
public class ReviewHintController {

    private final ReviewHintHandler handler;

    @PutMapping("/api/admin/hints/{hintId}/review")
    ReviewHintResponse review(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID hintId, @Valid @RequestBody ReviewHintCommand command) {
        return handler.handle(UUID.fromString(jwt.getSubject()), hintId, command);
    }
}
