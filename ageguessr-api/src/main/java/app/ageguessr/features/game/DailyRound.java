package app.ageguessr.features.game;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One photo of the game of a day (midnight UTC), the same for every player. {@code photoId}
 * becomes null when the person is excluded (V12, ON DELETE SET NULL): the round keeps its
 * place in the day but nothing about the person. Whether it is still playable is read, not
 * stored (see {@link DailyRoundStatus}).
 */
@Entity
@Table(name = "daily_rounds")
@IdClass(DailyRoundId.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyRound {

    @Id
    private LocalDate gameDate;

    /** 1-based order of the round in the day. */
    @Id
    private short position;

    private UUID photoId;

    private Instant drawnAt;
}
