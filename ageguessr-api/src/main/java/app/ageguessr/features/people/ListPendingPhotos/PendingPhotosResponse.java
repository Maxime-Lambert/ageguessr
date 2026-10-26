package app.ageguessr.features.people.ListPendingPhotos;

import app.ageguessr.features.people.PhotoDateSource;
import app.ageguessr.features.people.ReviewStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The curation queue. Admin-only, so it may show what the public endpoint never does:
 * name, Commons file and source page.
 */
public record PendingPhotosResponse(long totalPending, List<Item> items) {

    public record Item(
            UUID photoId,
            PersonView person,
            LocalDate takenFrom,
            LocalDate takenTo,
            int ageAtPhoto,
            PhotoDateSource dateSource,
            String commonsFile,
            String sourceUrl,
            String licenseShortName,
            String licenseUrl,
            String authorText,
            /** False for a living person while approving them is disabled (CurationProperties). */
            boolean approvable,
            /** The person's other photos, to see the age gap at a glance. */
            List<OtherPhoto> otherPhotos) {}

    public record PersonView(
            UUID id,
            String wikidataId,
            String nameEn,
            String nameFr,
            LocalDate birthDate,
            LocalDate deathDate,
            int sitelinks) {}

    public record OtherPhoto(UUID photoId, LocalDate takenFrom, int ageAtPhoto, ReviewStatus reviewStatus) {}
}
