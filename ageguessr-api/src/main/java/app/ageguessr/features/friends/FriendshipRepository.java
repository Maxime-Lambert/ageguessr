package app.ageguessr.features.friends;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FriendshipRepository extends JpaRepository<Friendship, UUID> {

    /** The relation of a pair, whatever its direction: there is at most one (V18). */
    @Query(
            """
            SELECT f FROM Friendship f
            WHERE (f.requesterId = :a AND f.addresseeId = :b) OR (f.requesterId = :b AND f.addresseeId = :a)
            """)
    Optional<Friendship> findBetween(@Param("a") UUID a, @Param("b") UUID b);

    @Query(
            """
            SELECT COUNT(f) FROM Friendship f
            WHERE f.status = app.ageguessr.features.friends.FriendshipStatus.ACCEPTED
              AND (f.requesterId = :userId OR f.addresseeId = :userId)
            """)
    long countFriendsOf(@Param("userId") UUID userId);

    long countByRequesterIdAndStatus(UUID requesterId, FriendshipStatus status);

    /** The accounts an account is friends with: the group of its comparison and leaderboard. */
    @Query(
            """
            SELECT CASE WHEN f.requesterId = :userId THEN f.addresseeId ELSE f.requesterId END
            FROM Friendship f
            WHERE f.status = app.ageguessr.features.friends.FriendshipStatus.ACCEPTED
              AND (f.requesterId = :userId OR f.addresseeId = :userId)
            """)
    List<UUID> findFriendIdsOf(@Param("userId") UUID userId);

    /** One relation of an account, seen from it: the other account's username, never its id. */
    interface RelationRow {
        UUID getId();

        String getStatus();

        boolean getOutgoing();

        String getUsername();

        Instant getSince();
    }

    /**
     * SQL rather than JPQL: the username belongs to the auth feature, which friends never
     * imports (ArchitectureTests), as the challenge leaderboard does. Read at each request,
     * so a renamed account shows its new name.
     */
    @Query(
            value =
                    """
                    SELECT f.id AS id, f.status AS status, (f.requester_id = :userId) AS outgoing,
                           u.username AS username, COALESCE(f.accepted_at, f.created_at) AS since
                    FROM friendships f
                    JOIN users u ON u.id = CASE WHEN f.requester_id = :userId THEN f.addressee_id ELSE f.requester_id END
                    WHERE f.requester_id = :userId OR f.addressee_id = :userId
                    """,
            nativeQuery = true)
    List<RelationRow> findRelationsOf(@Param("userId") UUID userId);

    /**
     * The account a username names, whatever its case (as the unique index), if its email
     * is verified: an account that cannot sign in is not a friend to add. SQL, as above.
     */
    @Query(
            value = "SELECT id FROM users WHERE LOWER(username) = LOWER(:username) AND email_verified",
            nativeQuery = true)
    Optional<UUID> findVerifiedUserIdByUsername(@Param("username") String username);

    @Query(value = "SELECT username FROM users WHERE id = :userId", nativeQuery = true)
    Optional<String> findUsernameOf(@Param("userId") UUID userId);

    interface UsernameRow {
        UUID getId();

        String getUsername();
    }

    @Query(value = "SELECT id AS id, username AS username FROM users WHERE id IN (:userIds)", nativeQuery = true)
    List<UsernameRow> findUsernamesOf(@Param("userIds") List<UUID> userIds);
}
