package app.ageguessr.features.people.GetPhotoForReview;

import app.ageguessr.features.people.PhotoImage;
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
    public PhotoImage handle(GetPhotoForReviewQuery query) {
        return images.findById(query.photoId()).orElseThrow(() -> new NotFoundException("Photo not found"));
    }
}
