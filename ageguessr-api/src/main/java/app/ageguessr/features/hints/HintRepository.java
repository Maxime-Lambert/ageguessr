package app.ageguessr.features.hints;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HintRepository extends JpaRepository<Hint, UUID> {
    @Query("SELECT h.dedupeKey FROM Hint h")
    Set<String> findAllDedupeKeys();

    @Query("SELECT h.dedupeKey FROM Hint h WHERE h.retiredAt IS NOT NULL")
    Set<String> findRetiredDedupeKeys();

    /**
     * Sets or clears (null) the retirement of these keys; the caller keeps each batch small.
     * Clears the persistence context, as the other bulk updates do: no stale hint after it.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Hint h SET h.retiredAt = :retiredAt WHERE h.dedupeKey IN :keys")
    int setRetiredAt(@Param("keys") Collection<String> keys, @Param("retiredAt") Instant retiredAt);

    /**
     * Oldest first; the id breaks ties, an import giving a whole batch the same date. A
     * retired hint no longer reaches a player: it is not worth an admin's review.
     */
    List<Hint> findByReviewStatusAndRetiredAtIsNullOrderByCreatedAtAscIdAsc(HintReviewStatus status, Pageable page);

    long countByReviewStatusAndRetiredAtIsNull(HintReviewStatus status);

    @Query(
            """
            SELECT new app.ageguessr.features.hints.HintCount(h.year, h.category, h.audience, COUNT(h))
            FROM Hint h
            WHERE h.reviewStatus = app.ageguessr.features.hints.HintReviewStatus.APPROVED
              AND h.retiredAt IS NULL
            GROUP BY h.year, h.category, h.audience
            """)
    List<HintCount> countPlayable();

    /**
     * The approved hints, not retired, a player of these audiences can be shown for a year, all
     * categories together (a year has a few hundred at most), never citing the person to
     * guess. Sorted by id, so that the same hint is picked for everyone.
     */
    @Query(
            """
            SELECT h FROM Hint h
            WHERE h.reviewStatus = app.ageguessr.features.hints.HintReviewStatus.APPROVED
              AND h.retiredAt IS NULL
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
