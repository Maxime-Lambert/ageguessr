package app.ageguessr.features.game;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
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
 * The histories of several accounts at once, by the rules of PlayerHistory: two queries
 * whatever the number of accounts (their rounds, then the state of the days they played).
 * Read by the friends' comparison and leaderboard, which never see a guess, a name or a
 * photo: only points, days and statistics.
 */
@Service
@RequiredArgsConstructor
public class PlayerHistories {

    private final GamePlayRepository plays;
    private final DailyRoundRepository rounds;
    private final Clock clock;

    /** One account's rounds (points only once finished) and what PlayerHistory makes of them. */
    public record Record(List<PlayerHistory.Play> plays, PlayerHistory.Result result) {}

    public static PlayerHistory.Play toPlay(GamePlay play) {
        boolean finished = play.step() == RoundStep.FINISHED;
        return new PlayerHistory.Play(play.getGameDate(), play.getPosition(), finished, finished ? play.points() : 0);
    }

    /** Today, in the time zone of the draw (UTC, ClockConfig). */
    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** @return a record for every account given, empty for one that never played */
    @Transactional(readOnly = true)
    public Map<UUID, Record> of(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<PlayerHistory.Play>> byUser = plays.findByUserIdIn(userIds).stream()
                .collect(Collectors.groupingBy(
                        GamePlay::getUserId, Collectors.mapping(PlayerHistories::toPlay, Collectors.toList())));
        Set<LocalDate> dates = byUser.values().stream()
                .flatMap(List::stream)
                .map(PlayerHistory.Play::date)
                .collect(Collectors.toSet());
        Map<LocalDate, Map<Short, Boolean>> playable = new HashMap<>();
        if (!dates.isEmpty()) {
            for (DatedRoundState state : rounds.findStatesOn(dates)) {
                playable.computeIfAbsent(state.gameDate(), date -> new HashMap<>())
                        .put(state.position(), state.status() == DailyRoundStatus.PLAYABLE);
            }
        }
        LocalDate today = today();
        Map<UUID, Record> records = new HashMap<>();
        for (UUID userId : userIds) {
            List<PlayerHistory.Play> own = byUser.getOrDefault(userId, List.of());
            records.put(userId, new Record(own, PlayerHistory.compute(own, playable, today)));
        }
        return records;
    }
}
