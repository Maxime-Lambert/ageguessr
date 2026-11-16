package app.ageguessr.features.game;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A day's rounds as one player sees them, with the score: shared by today's game and a
 * past day's, so that both read the same (a component, not one handler calling another).
 */
@Component
@RequiredArgsConstructor
public class DayGames {

    private final GameRounds rounds;
    private final GamePlayRepository plays;
    private final RoundViews views;

    /** @param player empty for a visitor who never played: every round not started */
    public DayGameResponse of(LocalDate date, Optional<PlayerKey> player) {
        Map<Short, GamePlay> played = player.map(key -> plays.findOf(key, date)).orElse(List.of()).stream()
                .collect(Collectors.toMap(GamePlay::getPosition, Function.identity()));
        List<RoundView> roundViews = rounds.of(date).stream()
                .map(round -> views.of(round, played.get(round.position())))
                .toList();
        return new DayGameResponse(date, roundViews, GameScore.of(roundViews));
    }
}
