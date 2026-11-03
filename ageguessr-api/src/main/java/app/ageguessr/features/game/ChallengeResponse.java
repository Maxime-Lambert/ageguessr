package app.ageguessr.features.game;

import java.util.List;
import java.util.UUID;

/**
 * A challenge as one visitor sees it. The leaderboard only once the visitor has finished:
 * nobody plays knowing the scores to beat.
 *
 * @param rounds how many rounds the series has (fewer than {@code size} when the pool ran short)
 * @param creatorGuest the creator played without an account: the name is a nickname
 * @param seriesId to play it, once joined; null before
 * @param score the visitor's own, once joined
 */
public record ChallengeResponse(
        String code,
        int size,
        int rounds,
        String creatorName,
        boolean creatorGuest,
        Status status,
        UUID seriesId,
        GameScore score,
        List<LeaderboardEntry> leaderboard) {

    public enum Status {
        NOT_JOINED,
        PLAYING,
        FINISHED
    }

    /**
     * Only the name shown, the score and the rank: never an email or an internal id.
     *
     * @param rank equal points share a rank (1, 1, 3)
     * @param guest a nickname rather than an account's username: two people may pick the same
     * @param you this line is the visitor's
     */
    public record LeaderboardEntry(int rank, String name, boolean guest, int points, int max, boolean you) {}
}
