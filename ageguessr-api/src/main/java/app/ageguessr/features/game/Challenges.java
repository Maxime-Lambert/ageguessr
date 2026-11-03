package app.ageguessr.features.game;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What a visitor sees of a challenge: the series shared under a code, and its leaderboard. */
@Component
@RequiredArgsConstructor
public class Challenges {

    private final FreeSeriesRepository series;
    private final FreeSeriesPlayerRepository players;
    private final FreeSeriesPlayRepository plays;
    private final GameRounds rounds;
    private final SeriesViews views;

    /** Unknown and expired (purged) codes answer the same 404. */
    public FreeSeries byCode(String code) {
        return series.findByShareCode(ShareCodes.normalize(code)).orElseThrow(GameErrors::challengeNotFound);
    }

    public ChallengeResponse of(FreeSeries shared, Optional<PlayerKey> visitor) {
        List<GameRound> seriesRounds = rounds.of(shared);
        Map<PlayerKey, FreeSeriesPlayerRepository.PlayerName> names = players.findNamesOf(shared.getId()).stream()
                .collect(Collectors.toMap(name -> new PlayerKey(name.getUserId(), name.getGuestId()), Function.identity()));
        FreeSeriesPlayerRepository.PlayerName creator = names.get(shared.creator());
        boolean joined = visitor.map(names::containsKey).orElse(false);
        if (!joined) {
            return new ChallengeResponse(
                    shared.getShareCode(), shared.getSize(), seriesRounds.size(), nameOf(creator),
                    shared.getGuestId() != null, ChallengeResponse.Status.NOT_JOINED, null, null, List.of());
        }
        PlayerKey key = visitor.orElseThrow();
        GameScore score = views.of(shared, key).score();
        boolean finished = score.complete();
        return new ChallengeResponse(
                shared.getShareCode(),
                shared.getSize(),
                seriesRounds.size(),
                nameOf(creator),
                shared.getGuestId() != null,
                finished ? ChallengeResponse.Status.FINISHED : ChallengeResponse.Status.PLAYING,
                shared.getId(),
                score,
                finished ? leaderboard(shared, seriesRounds, names, key) : List.of());
    }

    /**
     * The players who finished, by points then by who finished first. Points over the
     * playable rounds only, as each player's own score: a round neutralized since is out
     * for everyone.
     */
    List<ChallengeResponse.LeaderboardEntry> leaderboard(
            FreeSeries shared,
            List<GameRound> seriesRounds,
            Map<PlayerKey, FreeSeriesPlayerRepository.PlayerName> names,
            PlayerKey visitor) {
        Set<Short> playable = seriesRounds.stream()
                .filter(GameRound::playable)
                .map(GameRound::position)
                .collect(Collectors.toSet());
        int max = playable.size() * Scoring.EXACT_FIRST_GUESS_ROUND;
        Map<PlayerKey, List<FreeSeriesPlay>> byPlayer = plays.findBySeriesId(shared.getId()).stream()
                .filter(play -> playable.contains(play.getPosition()))
                .collect(Collectors.groupingBy(play -> new PlayerKey(play.getUserId(), play.getGuestId())));
        record Finished(PlayerKey player, int points, Instant at) {}
        List<Finished> finished = new ArrayList<>();
        byPlayer.forEach((player, played) -> {
            boolean done = played.size() == playable.size()
                    && played.stream().allMatch(play -> play.step() == RoundStep.FINISHED);
            if (done && names.containsKey(player)) {
                int points = played.stream().mapToInt(FreeSeriesPlay::points).sum();
                Instant at = played.stream().map(FreeSeriesPlay::getFinishedAt).max(Comparator.naturalOrder()).orElseThrow();
                finished.add(new Finished(player, points, at));
            }
        });
        finished.sort(Comparator.comparingInt(Finished::points).reversed().thenComparing(Finished::at));
        List<ChallengeResponse.LeaderboardEntry> entries = new ArrayList<>();
        for (int i = 0; i < finished.size(); i++) {
            Finished line = finished.get(i);
            int rank = i > 0 && finished.get(i - 1).points() == line.points() ? entries.get(i - 1).rank() : i + 1;
            entries.add(new ChallengeResponse.LeaderboardEntry(
                    rank,
                    nameOf(names.get(line.player())),
                    line.player().guestId() != null,
                    line.points(),
                    max,
                    line.player().equals(visitor)));
        }
        return entries;
    }

    private static String nameOf(FreeSeriesPlayerRepository.PlayerName name) {
        return name == null ? null : name.getName();
    }
}
