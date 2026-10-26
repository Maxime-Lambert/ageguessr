package app.ageguessr.features.people.GetPhotoForReview;

import app.ageguessr.shared.web.ImageContentType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin only (SecurityConfig). no-store: a photo under review must not linger in any
 * cache - it may be rejected, or belong to a person removed on request.
 */
@RestController
@RequiredArgsConstructor
public class GetPhotoForReviewController {

    private final GetPhotoForReviewHandler handler;

    @GetMapping("/api/admin/photos/{photoId}/image")
    ResponseEntity<byte[]> image(@PathVariable UUID photoId) {
        ReviewImageResponse image = handler.handle(new GetPhotoForReviewQuery(photoId));
        return ResponseEntity.ok()
                .contentType(ImageContentType.of(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(image.content());
    }
}
