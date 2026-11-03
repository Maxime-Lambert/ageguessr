package app.ageguessr.features.game;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A free series (V15): photos drawn for one player, played with the rules of the daily
 * game. Shared, it becomes a challenge that others join by its code.
 */
@Entity
@Table(name = "free_series")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class FreeSeries {

    /** The lengths a player can choose (user decision, 2026-11-03). */
    public static final List<Integer> SIZES = List.of(5, 10, 20);

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Asked for: the series has fewer rounds when the pool runs short. */
    private short size;

    /** The hints aim at its year minus the age, as the daily game does with its day. */
    private LocalDate createdOn;

    private Instant createdAt;

    private UUID userId;

    private UUID guestId;

    /** Set when shared: the code of the challenge. */
    private String shareCode;

    private Instant sharedAt;

    public static FreeSeries create(PlayerKey creator, int size, Instant now, LocalDate today) {
        return FreeSeries.builder()
                .size((short) size)
                .createdOn(today)
                .createdAt(now)
                .userId(creator.userId())
                .guestId(creator.guestId())
                .build();
    }

    public PlayerKey creator() {
        return new PlayerKey(userId, guestId);
    }

    public boolean isCreatedBy(PlayerKey player) {
        return creator().equals(player);
    }

    public boolean shared() {
        return shareCode != null;
    }

    /** Once: sharing again keeps the code already given out. */
    public void share(String code, Instant now) {
        if (shared()) {
            throw new IllegalStateException("A series is shared once");
        }
        this.shareCode = code;
        this.sharedAt = now;
    }
}
