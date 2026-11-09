package app.ageguessr.features.game;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Whether a round of a given day can still be played, and on which photo: the history
 * reads many days at once.
 *
 * @param photoId null once the person was excluded (V12, ON DELETE SET NULL)
 */
public record DatedRoundState(LocalDate gameDate, short position, UUID photoId, DailyRoundStatus status) {}
