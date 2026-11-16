package app.ageguessr.features.game;

import java.time.LocalDate;
import java.util.List;

/**
 * A day's game for one player: today's (GetTodayGame) or a past day's (GetDayGame). Empty
 * rounds when nothing could be drawn that day (empty pool).
 */
public record DayGameResponse(LocalDate date, List<RoundView> rounds, GameScore score) {}
