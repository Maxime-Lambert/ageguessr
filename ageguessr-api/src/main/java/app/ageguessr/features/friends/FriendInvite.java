package app.ageguessr.features.friends;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** An account's personal invite link: one code, replaced on demand (V18). */
@Entity
@Table(name = "friend_invites")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendInvite {

    @Id
    private UUID userId;

    private String code;

    private Instant createdAt;

    public FriendInvite(UUID userId, String code, Instant now) {
        this.userId = userId;
        this.code = code;
        this.createdAt = now;
    }

    /** The old link stops working at once: its code is no longer in the table. */
    public void replace(String code, Instant now) {
        this.code = code;
        this.createdAt = now;
    }
}
