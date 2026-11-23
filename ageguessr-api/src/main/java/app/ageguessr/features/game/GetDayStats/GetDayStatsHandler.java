package app.ageguessr.features.game.GetDayStats;

import app.ageguessr.features.game.DayGameResponse;
import app.ageguessr.features.game.DayGames;
import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundView;
import app.ageguessr.features.game.DailyRoundStatus;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * How the others did on a day (testers, 2026-11-23): the share of players the asker beat,
 * the first guesses on average. Only once the asker finished the day: the averages would
 * give the ages away. Guests count as long as their plays are kept (7 days, PurgeGuestPlays):
 * an older day counts the accounts only.
 */
@Service
@RequiredArgsConstructor
public class GetDayStatsHandler {

    private final DayGames days;
    private final GamePlayRepository plays;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DayStatsResponse handle(LocalDate date, Optional<PlayerKey> player) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw GameErrors.gameClosed();
        }
        DayGameResponse game = days.of(date, player);
        if (game.rounds().isEmpty()) {
            throw new NotFoundException("No game on this day");
        }
        if (player.isEmpty() || !game.score().complete()) {
            throw GameErrors.dayNotFinished();
        }
        Set<Short> playable = game.rounds().stream()
                .filter(round -> round.status() == DailyRoundStatus.PLAYABLE)
                .map(RoundView::position)
                .collect(Collectors.toSet());
        return DayStats.of(plays.findByGameDate(date), playable, player.get());
    }
}
