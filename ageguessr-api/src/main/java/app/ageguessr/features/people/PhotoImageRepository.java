package app.ageguessr.features.people;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoImageRepository extends JpaRepository<PhotoImage, UUID> {

    /**
     * Resolves the public id in one query and ignores excluded people and photos not
     * approved at curation, so the caller cannot tell "unknown id" from "removed on
     * request" or "not reviewed yet".
     */
    @Query(
            """
            SELECT i FROM PhotoImage i, PersonPhoto ph, Person p
            WHERE ph.publicId = :publicId
              AND i.photoId = ph.id
              AND p.id = ph.personId
              AND p.excluded = false
              AND ph.reviewStatus = app.ageguessr.features.people.ReviewStatus.APPROVED
            """)
    Optional<PhotoImage> findServableByPublicId(@Param("publicId") UUID publicId);
}
