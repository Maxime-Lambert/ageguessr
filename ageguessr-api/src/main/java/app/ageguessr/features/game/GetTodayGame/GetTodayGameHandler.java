package app.ageguessr.features.game.GetTodayGame;

import app.ageguessr.features.game.DayGameResponse;
import app.ageguessr.features.game.DayGames;
import app.ageguessr.features.game.DrawDailyGame.DrawDailyGameHandler;
import app.ageguessr.features.game.GameProperties;
import app.ageguessr.features.game.PlayerKey;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Not @Transactional: the draw runs in its own transactions, and a read-only transaction
 * around it would refuse its inserts. The reads that follow need no shared transaction,
 * the entities having no lazy relations.
 */
@Service
@RequiredArgsConstructor
public class GetTodayGameHandler {

    private final DrawDailyGameHandler draw;
    private final GameProperties properties;
    private final DayGames days;
    private final Clock clock;

    public DayGameResponse handle(Optional<PlayerKey> player) {
        LocalDate today = LocalDate.now(clock);
        // The scheduler draws at midnight and at startup; drawing here too covers a draw
        // that failed, and the first visitor of the day draws it. Off with the flag.
        if (properties.drawEnabled()) {
            draw.drawIfMissing(today);
        }
        return days.of(today, player);
    }
}
