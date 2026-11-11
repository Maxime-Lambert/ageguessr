package app.ageguessr.features.friends;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A pair of accounts: a request from requesterId to addresseeId, then a friendship once
 * accepted. One row per pair whatever its direction (V18 unique index). Declining,
 * cancelling and removing delete the row.
 */
@Entity
@Table(name = "friendships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friendship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID requesterId;

    private UUID addresseeId;

    @Enumerated(EnumType.STRING)
    private FriendshipStatus status;

    private Instant createdAt;

    private Instant acceptedAt;

    private Friendship(UUID requesterId, UUID addresseeId, FriendshipStatus status, Instant now) {
        if (requesterId.equals(addresseeId)) {
            throw new IllegalArgumentException("An account cannot be its own friend");
        }
        this.requesterId = requesterId;
        this.addresseeId = addresseeId;
        this.status = status;
        this.createdAt = now;
        this.acceptedAt = status == FriendshipStatus.ACCEPTED ? now : null;
    }

    public static Friendship request(UUID requesterId, UUID addresseeId, Instant now) {
        return new Friendship(requesterId, addresseeId, FriendshipStatus.PENDING, now);
    }

    /** Already accepted: a friendship in the state a request ends up in once accepted. */
    public static Friendship accepted(UUID requesterId, UUID addresseeId, Instant now) {
        return new Friendship(requesterId, addresseeId, FriendshipStatus.ACCEPTED, now);
    }

    public void accept(Instant now) {
        if (status == FriendshipStatus.ACCEPTED) {
            return;
        }
        status = FriendshipStatus.ACCEPTED;
        acceptedAt = now;
    }

    public boolean isAccepted() {
        return status == FriendshipStatus.ACCEPTED;
    }

    public boolean involves(UUID userId) {
        return requesterId.equals(userId) || addresseeId.equals(userId);
    }

    /** The other account of the pair, seen from one of its two. */
    public UUID otherThan(UUID userId) {
        if (!involves(userId)) {
            throw new IllegalArgumentException("Not part of this friendship");
        }
        return requesterId.equals(userId) ? addresseeId : requesterId;
    }
}
