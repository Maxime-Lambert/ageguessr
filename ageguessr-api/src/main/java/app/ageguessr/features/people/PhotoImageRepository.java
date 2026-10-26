package app.ageguessr.features.people;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PhotoImageRepository extends JpaRepository<PhotoImage, UUID> {

    /**
     * Resolves the public id in one query and ignores excluded people, so the caller
     * cannot tell "unknown id" from "person removed on request".
     */
    @Query(
            """
            SELECT i FROM PhotoImage i, PersonPhoto ph, Person p
            WHERE ph.publicId = :publicId
              AND i.photoId = ph.id
              AND p.id = ph.personId
              AND p.excluded = false
            """)
    Optional<PhotoImage> findServableByPublicId(@Param("publicId") UUID publicId);
}
