package app.ageguessr.features.people.GetPhotoForReview;

import app.ageguessr.features.people.PhotoImageRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Any review status, unlike the public endpoint: the admin must see a photo to judge it. */
@Service
@RequiredArgsConstructor
public class GetPhotoForReviewHandler {

    private final PhotoImageRepository images;

    @Transactional(readOnly = true)
    public ReviewImageResponse handle(GetPhotoForReviewQuery query) {
        return images.findById(query.photoId())
                .map(image -> new ReviewImageResponse(image.getContent(), image.getContentType()))
                .orElseThrow(() -> new NotFoundException("Photo not found"));
    }
}
