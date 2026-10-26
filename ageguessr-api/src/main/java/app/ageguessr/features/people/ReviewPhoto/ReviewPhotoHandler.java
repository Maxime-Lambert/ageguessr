package app.ageguessr.features.people.ReviewPhoto;

import app.ageguessr.features.people.CurationProperties;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.features.people.ReviewStatus;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records a curation decision. Every transition is allowed, undo included (back to
 * PENDING). Two refusals:
 * <ul>
 *   <li>approving a living person while CurationProperties forbids it;
 *   <li>a decision that would give the person two non-rejected photos of the same age or
 *       year - possible when reopening a rejected photo whose slot another one took. The
 *       partial unique indexes of V4 refuse it, and it becomes a 409 instead of a 500.
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReviewPhotoHandler {

    static final String LIVING_PERSON_APPROVAL_DISABLED = "LIVING_PERSON_APPROVAL_DISABLED";
    static final String PHOTO_SLOT_TAKEN = "PHOTO_SLOT_TAKEN";

    private final PersonPhotoRepository photos;
    private final PersonRepository people;
    private final CurationProperties curation;
    private final Clock clock;

    @Transactional
    public ReviewPhotoResponse handle(UUID adminId, UUID photoId, ReviewPhotoCommand command) {
        PersonPhoto photo = photos.findById(photoId).orElseThrow(() -> new NotFoundException("Photo not found"));
        // Photos of an excluded person are deleted by the V4 trigger; this guards the gap
        // between the two in case the trigger is ever bypassed.
        Person person = people.findById(photo.getPersonId())
                .filter(p -> !p.isExcluded())
                .orElseThrow(() -> new NotFoundException("Photo not found"));
        if (command.decision() == ReviewStatus.APPROVED && !curation.canApprove(person)) {
            throw new ConflictException(
                    LIVING_PERSON_APPROVAL_DISABLED,
                    "Photos of living people cannot be approved until image rights are settled");
        }
        photo.setReviewStatus(command.decision());
        photo.setReviewedAt(clock.instant());
        photo.setReviewedBy(adminId);
        try {
            photos.saveAndFlush(photo);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException(
                    PHOTO_SLOT_TAKEN, "Another photo of this person already has this age or year");
        }
        return new ReviewPhotoResponse(photo.getId(), photo.getReviewStatus());
    }
}
