package app.ageguessr.features.hints;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
