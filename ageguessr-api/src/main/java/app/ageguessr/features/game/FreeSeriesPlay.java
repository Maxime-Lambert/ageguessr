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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One player's round of a free series (V15), created by the first guess. */
@Entity
@Table(name = "free_series_plays")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(access = AccessLevel.PRIVATE)
public class FreeSeriesPlay implements PlayedRound {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID seriesId;

    private short position;

    private UUID userId;

    private UUID guestId;

    @Embedded
    private RoundProgress progress;

    /** Two tabs playing the same step at once: the second write fails instead of overwriting. */
    @Version
    private int version;

    public static FreeSeriesPlay start(
            UUID seriesId, short position, PlayerKey player, Language language, int guess, int age, Instant now) {
        return FreeSeriesPlay.builder()
                .seriesId(seriesId)
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
