package app.ageguessr.features.auth;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // @Modifying bulk queries need their own transaction - unlike save()/findById(),
    // they are not covered by SimpleJpaRepository's class-level @Transactional.
    @Transactional
    @Modifying
    @Query(
            """
            UPDATE RefreshToken t
            SET t.revokedAt = :revokedAt
            WHERE t.userId = :userId AND t.revokedAt IS NULL
            """)
    void revokeAllActiveForUser(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
