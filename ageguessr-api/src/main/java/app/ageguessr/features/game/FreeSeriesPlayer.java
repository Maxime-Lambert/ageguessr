package app.ageguessr.features.game;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A player of a free series: its creator, then whoever joins the challenge. Only a player
 * may play its rounds, once (V15 unique indexes).
 */
@Entity
@Table(name = "free_series_players")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class FreeSeriesPlayer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID seriesId;

    private UUID userId;

    private UUID guestId;

    /** A guest's name in this challenge only; an account shows its username instead. */
    private String nickname;

    private Instant joinedAt;

    public static FreeSeriesPlayer of(UUID seriesId, PlayerKey player, String nickname, Instant now) {
        if (player.userId() != null && nickname != null) {
            throw new IllegalArgumentException("An account plays under its username");
        }
        return FreeSeriesPlayer.builder()
                .seriesId(seriesId)
                .userId(player.userId())
                .guestId(player.guestId())
                .nickname(nickname)
                .joinedAt(now)
                .build();
    }

    public PlayerKey key() {
        return new PlayerKey(userId, guestId);
    }

    /** A guest creator names themselves when sharing: the challenge shows who sent it. */
    public void name(String nickname) {
        if (userId != null) {
            throw new IllegalStateException("An account plays under its username");
        }
        this.nickname = nickname;
    }
}
