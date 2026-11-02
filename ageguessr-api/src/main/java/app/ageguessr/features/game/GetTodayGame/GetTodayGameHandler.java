package app.ageguessr.features.game.GetTodayGame;

import app.ageguessr.features.game.DailyRoundStatus;
import app.ageguessr.features.game.DrawDailyGame.DrawDailyGameHandler;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GameProperties;
import app.ageguessr.features.game.GameRound;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundStep;
import app.ageguessr.features.game.RoundView;
import app.ageguessr.features.game.RoundViews;
import app.ageguessr.features.game.Scoring;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final GameRounds rounds;
    private final GamePlayRepository plays;
    private final RoundViews views;
    private final Clock clock;

    public TodayGameResponse handle(Optional<PlayerKey> player) {
        LocalDate today = LocalDate.now(clock);
        // The scheduler draws at midnight and at startup; drawing here too covers a draw
        // that failed, and the first visitor of the day draws it. Off with the flag.
        if (properties.drawEnabled()) {
            draw.drawIfMissing(today);
        }
        Map<Short, GamePlay> played = player.map(key -> plays.findOf(key, today)).orElse(List.of()).stream()
                .collect(Collectors.toMap(GamePlay::getPosition, Function.identity()));
        List<GameRound> day = rounds.of(today);
        List<RoundView> roundViews = day.stream()
                .map(round -> views.of(round, played.get(round.position())))
                .toList();
        return new TodayGameResponse(today, roundViews, score(roundViews));
    }

    private static TodayGameResponse.Score score(List<RoundView> rounds) {
        List<RoundView> playable = rounds.stream()
                .filter(round -> round.status() == DailyRoundStatus.PLAYABLE)
                .toList();
        int points = playable.stream()
                .filter(round -> round.step() == RoundStep.FINISHED)
                .mapToInt(round -> round.result().points())
                .sum();
        boolean complete = playable.stream().allMatch(round -> round.step() == RoundStep.FINISHED);
        return new TodayGameResponse.Score(points, playable.size() * Scoring.EXACT_FIRST_GUESS_ROUND, complete);
    }
}
