package app.ageguessr.features.people;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PersonPhotoRepository extends JpaRepository<PersonPhoto, UUID> {
    List<PersonPhoto> findByPersonId(UUID personId);

    List<PersonPhoto> findByPersonIdIn(Collection<UUID> personIds);

    /**
     * Photos waiting for curation, best-known people first. Within a person, oldest shot
     * first; the photo id breaks remaining ties so that the order is stable.
     */
    @Query(
            """
            SELECT new app.ageguessr.features.people.PendingPhoto(ph, p)
            FROM PersonPhoto ph, Person p
            WHERE p.id = ph.personId
              AND ph.reviewStatus = app.ageguessr.features.people.ReviewStatus.PENDING
              AND p.excluded = false
            ORDER BY p.sitelinks DESC, p.id, ph.takenFrom, ph.id
            """)
    List<PendingPhoto> findPending(Pageable page);

    @Query(
            """
            SELECT COUNT(ph)
            FROM PersonPhoto ph, Person p
            WHERE p.id = ph.personId
              AND ph.reviewStatus = app.ageguessr.features.people.ReviewStatus.PENDING
              AND p.excluded = false
            """)
    long countPending();

    /** How many photos of people not excluded there are at each age, per review status. */
    record AgeCount(int age, ReviewStatus status, long photos) {}

    @Query(
            """
            SELECT new app.ageguessr.features.people.PersonPhotoRepository$AgeCount(
                ph.ageAtPhoto, ph.reviewStatus, COUNT(ph))
            FROM PersonPhoto ph, Person p
            WHERE p.id = ph.personId
              AND p.excluded = false
            GROUP BY ph.ageAtPhoto, ph.reviewStatus
            """)
    List<AgeCount> countByAge();
}
