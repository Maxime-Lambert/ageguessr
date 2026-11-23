package app.ageguessr.features.game.GetDayStats;

import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundStep;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The stats of a day from its plays, computed in memory: a day holds one play per player and
 * round, a few thousand rows at most at today's audience. Only the players who finished
 * every playable round count, so that a total compares with a total.
 */
public final class DayStats {

    /** Under this many players, no share and no average: they would say little, or too much. */
    public static final int MINIMUM_PLAYERS = 10;

    private DayStats() {}

    /**
     * @param plays every play of the day, all players
     * @param playable the positions still in the game (a withdrawn photo counts for no one)
     * @param asker the player asking, who finished the day
     */
    public static DayStatsResponse of(List<GamePlay> plays, Set<Short> playable, PlayerKey asker) {
        Map<PlayerKey, List<GamePlay>> finished = plays.stream()
                .filter(play -> playable.contains(play.getPosition()))
                .collect(Collectors.groupingBy(DayStats::keyOf))
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() == playable.size()
                        && entry.getValue().stream().allMatch(play -> play.progress().step() == RoundStep.FINISHED))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        int players = finished.size();
        if (players < MINIMUM_PLAYERS || !finished.containsKey(asker)) {
            return new DayStatsResponse(players, null, List.of());
        }
        Map<PlayerKey, Integer> totals = finished.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> total(entry.getValue())));
        int mine = totals.get(asker);
        long below = totals.values().stream().filter(total -> total < mine).count();
        int betterThan = (int) Math.round(100.0 * below / (players - 1));
        List<DayStatsResponse.RoundStats> rounds = finished.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.groupingBy(
                        GamePlay::getPosition, Collectors.averagingInt(play -> play.progress().getFirstGuess())))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(entry -> new DayStatsResponse.RoundStats(entry.getKey(), (int) Math.round(entry.getValue())))
                .toList();
        return new DayStatsResponse(players, betterThan, rounds);
    }

    private static PlayerKey keyOf(GamePlay play) {
        return new PlayerKey(play.getUserId(), play.getGuestId());
    }

    private static int total(List<GamePlay> plays) {
        return plays.stream().mapToInt(play -> play.progress().points()).sum();
    }
}
