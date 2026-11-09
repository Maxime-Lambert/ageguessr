package app.ageguessr.features.game.GetHistory;

import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.DailyRoundStatus;
import app.ageguessr.features.game.DatedRoundState;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GameRound;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.RoundReveal;
import app.ageguessr.features.game.RoundStep;
import app.ageguessr.features.game.GetHistory.HistoryCalculator.Play;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An account's history: the statistics over every day played, and the rounds of one page
 * of days. Every played round is read at once (3 a day at most, about 1 100 a year), then
 * HistoryCalculator does the arithmetic; only the page's days load their photo and person.
 */
@Service
@RequiredArgsConstructor
public class GetHistoryHandler {

    static final int PAGE_SIZE = 30;

    private final GamePlayRepository plays;
    private final DailyRoundRepository rounds;
    private final GameRounds gameRounds;
    private final Clock clock;

    @Transactional(readOnly = true)
    public HistoryResponse handle(UUID userId, int page) {
        List<GamePlay> played = plays.findByUserId(userId);
        Set<LocalDate> dates = played.stream().map(GamePlay::getGameDate).collect(Collectors.toSet());
        Map<LocalDate, Map<Short, Boolean>> playable = new HashMap<>();
        if (!dates.isEmpty()) {
            for (DatedRoundState state : rounds.findStatesOn(dates)) {
                playable.computeIfAbsent(state.gameDate(), date -> new HashMap<>())
                        .put(state.position(), state.status() == DailyRoundStatus.PLAYABLE);
            }
        }
        HistoryCalculator.Result result = HistoryCalculator.compute(
                played.stream().map(GetHistoryHandler::toPlay).toList(), playable, LocalDate.now(clock));

        List<HistoryCalculator.Day> days = result.days();
        // In long: a huge page number would overflow an int and go negative.
        int from = (int) Math.min((long) page * PAGE_SIZE, days.size());
        int to = Math.min(from + PAGE_SIZE, days.size());
        List<HistoryResponse.Day> pageDays = days.subList(from, to).stream()
                .map(day -> toDay(day, played))
                .toList();
        HistoryCalculator.Stats stats = result.stats();
        return new HistoryResponse(
                new HistoryResponse.Stats(
                        stats.gamesPlayed(),
                        stats.averagePoints(),
                        stats.bestPoints(),
                        stats.currentStreak(),
                        stats.bestStreak()),
                pageDays,
                page,
                to < days.size());
    }

    private static Play toPlay(GamePlay play) {
        boolean finished = play.step() == RoundStep.FINISHED;
        return new Play(play.getGameDate(), play.getPosition(), finished, finished ? play.points() : 0);
    }

    private HistoryResponse.Day toDay(HistoryCalculator.Day day, List<GamePlay> played) {
        Map<Short, GameRound> ofDay = gameRounds.of(day.date()).stream()
                .collect(Collectors.toMap(GameRound::position, round -> round));
        List<HistoryResponse.Round> rounds = played.stream()
                .filter(play -> play.getGameDate().equals(day.date()))
                .sorted(Comparator.comparing(GamePlay::getPosition))
                .map(play -> toRound(play, ofDay.get(play.getPosition())))
                .toList();
        return new HistoryResponse.Day(day.date(), day.points(), day.max(), day.complete(), rounds);
    }

    /**
     * The answer only for a finished round: today's round still being played must say
     * nothing more here than on the game screen. Nor for a neutralized one: its photo was
     * rejected or its person excluded, so neither is shown again.
     */
    private static HistoryResponse.Round toRound(GamePlay play, GameRound round) {
        boolean finished = play.step() == RoundStep.FINISHED;
        boolean neutralized = round == null || !round.playable();
        boolean reveal = finished && !neutralized;
        return new HistoryResponse.Round(
                play.getPosition(),
                finished,
                neutralized,
                reveal ? RoundReveal.name(round.person(), play.getLanguage()) : null,
                reveal ? round.age() : null,
                play.getFirstGuess(),
                play.getSecondGuess(),
                finished ? play.points() : null);
    }
}
