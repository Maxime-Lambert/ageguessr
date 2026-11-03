package app.ageguessr.features.game.GetTodayGame;

import app.ageguessr.features.game.GameScore;
import app.ageguessr.features.game.RoundView;
import java.time.LocalDate;
import java.util.List;

/**
 * Today's game for one player. Empty rounds when nothing could be drawn (empty pool).
 */
public record TodayGameResponse(LocalDate date, List<RoundView> rounds, GameScore score) {}
