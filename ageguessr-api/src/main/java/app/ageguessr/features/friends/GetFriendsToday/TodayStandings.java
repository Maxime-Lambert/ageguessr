package app.ageguessr.features.friends.GetFriendsToday;

import app.ageguessr.features.game.PlayerHistory;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Today's daily game of a group, by the rules of PlayerHistory: a day is complete when
 * every round still playable is finished. Pure, so that each rule is tested on its own.
 *
 * <ul>
 *   <li>Locked until the caller's own day is complete: the points of a friend would hint
 *       at the answers of rounds the caller has not played yet.
 *   <li>A friend's points only once their day is complete: until then « in progress ».
 *   <li>Complete first, by points, then in progress, then not played; ties by username.
 * </ul>
 */
final class TodayStandings {

    private TodayStandings() {}

    enum Status {
        COMPLETE,
        IN_PROGRESS,
        NOT_PLAYED
    }

    /** One member of the group, as the use case reads them. */
    record Member(String username, boolean me, List<PlayerHistory.Play> plays, List<PlayerHistory.Day> days) {}

    record RoundPoints(short position, int points) {}

    /** @param points null unless complete, as max and rounds */
    record Standing(String username, boolean me, Status status, Integer points, Integer max, List<RoundPoints> rounds) {}

    static boolean isUnlocked(Member me, LocalDate today) {
        return dayOf(me, today).map(PlayerHistory.Day::complete).orElse(false);
    }

    static List<Standing> rank(List<Member> members, LocalDate today) {
        return members.stream()
                .map(member -> standingOf(member, today))
                .sorted(Comparator.comparing(Standing::status)
                        .thenComparing(standing -> standing.points() == null ? 0 : -standing.points())
                        .thenComparing(standing -> standing.username().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static Standing standingOf(Member member, LocalDate today) {
        Optional<PlayerHistory.Day> day = dayOf(member, today);
        if (day.isEmpty()) {
            return new Standing(member.username(), member.me(), Status.NOT_PLAYED, null, null, null);
        }
        if (!day.get().complete()) {
            return new Standing(member.username(), member.me(), Status.IN_PROGRESS, null, null, null);
        }
        List<RoundPoints> rounds = member.plays().stream()
                .filter(play -> play.date().equals(today) && play.finished())
                .sorted(Comparator.comparing(PlayerHistory.Play::position))
                .map(play -> new RoundPoints(play.position(), play.points()))
                .toList();
        return new Standing(
                member.username(), member.me(), Status.COMPLETE, day.get().points(), day.get().max(), rounds);
    }

    private static Optional<PlayerHistory.Day> dayOf(Member member, LocalDate today) {
        return member.days().stream().filter(day -> day.date().equals(today)).findFirst();
    }
}
