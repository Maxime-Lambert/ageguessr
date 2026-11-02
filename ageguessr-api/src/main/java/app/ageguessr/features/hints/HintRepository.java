package app.ageguessr.features.hints;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HintRepository extends JpaRepository<Hint, UUID> {
    @Query("SELECT h.dedupeKey FROM Hint h")
    Set<String> findAllDedupeKeys();

    /** Oldest first; the id breaks ties, an import giving a whole batch the same date. */
    List<Hint> findByReviewStatusOrderByCreatedAtAscIdAsc(HintReviewStatus status, Pageable page);

    long countByReviewStatus(HintReviewStatus status);

    @Query(
            """
            SELECT new app.ageguessr.features.hints.HintCount(h.year, h.category, h.audience, COUNT(h))
            FROM Hint h
            WHERE h.reviewStatus = app.ageguessr.features.hints.HintReviewStatus.APPROVED
            GROUP BY h.year, h.category, h.audience
            """)
    List<HintCount> countApproved();

    /**
     * The approved hints a player of these audiences can be shown for a year, all
     * categories together (a year has a few hundred at most), never citing the person to
     * guess. Sorted by id, so that the same hint is picked for everyone.
     */
    @Query(
            """
            SELECT h FROM Hint h
            WHERE h.reviewStatus = app.ageguessr.features.hints.HintReviewStatus.APPROVED
              AND h.year = :year
              AND h.audience IN :audiences
              AND (h.subjectQid IS NULL OR h.subjectQid <> :personQid)
            ORDER BY h.id
            """)
    List<Hint> findEligible(
            @Param("year") int year,
            @Param("audiences") Collection<HintAudience> audiences,
            @Param("personQid") String personQid);
}
