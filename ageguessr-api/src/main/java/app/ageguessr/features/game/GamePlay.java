package app.ageguessr.features.game;

import app.ageguessr.shared.i18n.Language;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One player's round of a day (V13), created by the first guess. The rules of the round
 * are in {@link RoundProgress}, shared with the free series.
 */
@Entity
@Table(name = "game_plays")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class GamePlay implements PlayedRound {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private LocalDate gameDate;

    private short position;

    private UUID userId;

    private UUID guestId;

    @Embedded
    private RoundProgress progress;

    /** Two tabs playing the same step at once: the second write fails instead of overwriting. */
    @Version
    private int version;

    /** Starts the round with its first guess; an exact one ends it at once. */
    public static GamePlay start(
            LocalDate gameDate, short position, PlayerKey player, Language language, int guess, int age, Instant now) {
        return GamePlay.builder()
                .gameDate(gameDate)
                .position(position)
                .userId(player.userId())
                .guestId(player.guestId())
                .progress(RoundProgress.start(language, guess, age, now))
                .build();
    }

    @Override
    public RoundProgress progress() {
        return progress;
    }
}
