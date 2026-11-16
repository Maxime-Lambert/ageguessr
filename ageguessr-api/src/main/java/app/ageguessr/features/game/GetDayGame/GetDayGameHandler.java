package app.ageguessr.features.game.GetDayGame;

import app.ageguessr.features.game.DayGameResponse;
import app.ageguessr.features.game.DayGames;
import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Any day's game up to today, played from the calendar. Never draws: a day without a draw
 * stays without one (only today's game is drawn, by the scheduler or its first visitor).
 */
@Service
@RequiredArgsConstructor
public class GetDayGameHandler {

    private final DayGames days;
    private final Clock clock;

    public DayGameResponse handle(LocalDate date, Optional<PlayerKey> player) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw GameErrors.gameClosed();
        }
        DayGameResponse game = days.of(date, player);
        if (game.rounds().isEmpty()) {
            throw new NotFoundException("No game on this day");
        }
        return game;
    }
}
