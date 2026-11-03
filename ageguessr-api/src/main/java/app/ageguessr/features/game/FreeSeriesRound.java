package app.ageguessr.features.game;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One photo of a free series. {@code photoId} becomes null when the person is excluded
 * (V15, ON DELETE SET NULL), and the round is then neutralized, as in the daily game.
 */
@Entity
@Table(name = "free_series_rounds")
@IdClass(FreeSeriesRoundId.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FreeSeriesRound {

    @Id
    private UUID seriesId;

    /** 1-based order of the round in the series. */
    @Id
    private short position;

    private UUID photoId;
}
