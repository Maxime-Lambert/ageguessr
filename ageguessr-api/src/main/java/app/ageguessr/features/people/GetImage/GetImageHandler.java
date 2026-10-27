package app.ageguessr.features.people.GetImage;

import app.ageguessr.features.people.PhotoImageRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves a photo by its opaque public id. Every refusal is the same 404 - malformed id,
 * unknown id, photo pending or rejected at curation, person excluded on request - so the
 * endpoint cannot be used to learn anything about the pool.
 */
@Service
@RequiredArgsConstructor
public class GetImageHandler {

    private final PhotoImageRepository images;

    @Transactional(readOnly = true)
    public ImageResponse handle(GetImageQuery query) {
        UUID publicId;
        try {
            publicId = UUID.fromString(query.publicId());
        } catch (IllegalArgumentException e) {
            throw notFound();
        }
        return images.findServableByPublicId(publicId)
                .map(image -> new ImageResponse(image.getContent(), image.getContentType()))
                .orElseThrow(GetImageHandler::notFound);
    }

    private static NotFoundException notFound() {
        return new NotFoundException("Image not found");
    }
}
