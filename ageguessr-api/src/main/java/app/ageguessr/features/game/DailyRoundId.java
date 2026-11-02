package app.ageguessr.features.game;

import java.io.Serializable;
import java.time.LocalDate;

/** Primary key of {@link DailyRound}: a day and a position in it. */
public record DailyRoundId(LocalDate gameDate, short position) implements Serializable {}
