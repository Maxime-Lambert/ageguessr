package app.ageguessr.features.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {
    Optional<AccountToken> findByTokenHashAndPurpose(String tokenHash, AccountTokenPurpose purpose);

    /**
     * Conditional update rather than read-then-save: two concurrent submissions of the
     * same link both pass a "consumedAt == null" read, but only one of them can flip
     * the row here - the caller treats 0 updated rows as "already used".
     */
    @Transactional
    @Modifying
    @Query(
            """
            UPDATE AccountToken t
            SET t.consumedAt = :consumedAt
            WHERE t.id = :id AND t.consumedAt IS NULL
            """)
    int markConsumed(@Param("id") UUID id, @Param("consumedAt") Instant consumedAt);

    // @Modifying bulk queries need their own transaction - unlike save()/findById(),
    // they are not covered by SimpleJpaRepository's class-level @Transactional.
    @Transactional
    @Modifying
    @Query(
            """
            UPDATE AccountToken t
            SET t.consumedAt = :consumedAt
            WHERE t.userId = :userId AND t.purpose = :purpose AND t.consumedAt IS NULL
            """)
    void consumeAllActiveForUser(
            @Param("userId") UUID userId,
            @Param("purpose") AccountTokenPurpose purpose,
            @Param("consumedAt") Instant consumedAt);
}
