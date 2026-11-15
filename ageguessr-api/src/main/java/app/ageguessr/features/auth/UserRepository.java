package app.ageguessr.features.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, UUID id);

    /** Signed in or renewed a session: active again, any warning of deletion lifted. */
    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.lastActiveAt = :now, u.inactivityWarnedAt = NULL WHERE u.id = :id")
    void markActive(@Param("id") UUID id, @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query("UPDATE User u SET u.inactivityWarnedAt = :now WHERE u.id = :id")
    void markWarned(@Param("id") UUID id, @Param("now") Instant now);

    /** Accounts whose email was never confirmed, created before the date. */
    @Transactional
    @Modifying
    @Query("DELETE FROM User u WHERE u.emailVerified = false AND u.createdAt < :before")
    int deleteUnverifiedCreatedBefore(@Param("before") Instant before);

    /** Players inactive since before the date, not warned yet (admins never purged). */
    @Query(
            """
            SELECT u FROM User u
            WHERE u.emailVerified = true AND u.role = app.ageguessr.features.auth.UserRole.USER
              AND u.lastActiveAt < :before AND u.inactivityWarnedAt IS NULL
            """)
    List<User> findInactiveToWarn(@Param("before") Instant before);

    /**
     * Players inactive since before the date and warned before the other: the warning,
     * not the inactivity alone, starts the notice.
     */
    @Transactional
    @Modifying
    @Query(
            """
            DELETE FROM User u
            WHERE u.emailVerified = true AND u.role = app.ageguessr.features.auth.UserRole.USER
              AND u.lastActiveAt < :inactiveBefore AND u.inactivityWarnedAt < :warnedBefore
            """)
    int deleteInactive(@Param("inactiveBefore") Instant inactiveBefore, @Param("warnedBefore") Instant warnedBefore);
}
