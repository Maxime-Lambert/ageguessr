package app.ageguessr.features.people.ReviewPhoto;

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
public class ReviewPhotoController {

    private final ReviewPhotoHandler handler;

    @PutMapping("/api/admin/photos/{photoId}/review")
    ReviewPhotoResponse review(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID photoId, @Valid @RequestBody ReviewPhotoCommand command) {
        return handler.handle(UUID.fromString(jwt.getSubject()), photoId, command);
    }
}
