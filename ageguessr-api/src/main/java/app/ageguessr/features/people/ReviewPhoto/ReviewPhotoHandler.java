package app.ageguessr.features.people.ReviewPhoto;

import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.PhotoReportRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.reports.ReportStatus;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a curation decision. Every transition is allowed, undo included (back to
 * PENDING), except one that would give the person two non-rejected photos of the same age
 * or year - possible when reopening a rejected photo whose slot another one took. The
 * partial unique indexes of V4 refuse it, and it becomes a 409 instead of a 500.
 */
@Service
@RequiredArgsConstructor
public class ReviewPhotoHandler {

    static final String PHOTO_SLOT_TAKEN = "PHOTO_SLOT_TAKEN";

    private final PersonPhotoRepository photos;
    private final PersonRepository people;
    private final PhotoReportRepository reports;
    private final Clock clock;

    @Transactional
    public ReviewPhotoResponse handle(UUID adminId, UUID photoId, ReviewPhotoCommand command) {
        PersonPhoto photo = photos.findById(photoId).orElseThrow(() -> new NotFoundException("Photo not found"));
        // Photos of an excluded person are deleted by the V4 trigger; this guards the gap
        // between the two in case the trigger is ever bypassed.
        people.findById(photo.getPersonId())
                .filter(p -> !p.isExcluded())
                .orElseThrow(() -> new NotFoundException("Photo not found"));
        photo.setReviewStatus(command.decision());
        photo.setReviewedAt(clock.instant());
        photo.setReviewedBy(adminId);
        try {
            photos.saveAndFlush(photo);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    PHOTO_SLOT_TAKEN, "Another photo of this person already has this age or year");
        }
        // A decision deals with what players reported: the photo leaves the reported queue.
        reports.closeOpen(photo.getId(), ReportStatus.RESOLVED, clock.instant());
        return new ReviewPhotoResponse(photo.getId(), photo.getReviewStatus());
    }
}
