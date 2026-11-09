package app.ageguessr.features.game;

import java.time.LocalDate;

/** Whether a round of a given day can still be played: the history reads many days at once. */
public record DatedRoundState(LocalDate gameDate, short position, DailyRoundStatus status) {}
