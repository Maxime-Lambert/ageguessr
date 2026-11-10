package app.ageguessr.features.friends.GetFriendsToday;

import app.ageguessr.features.friends.FriendGroup;
import app.ageguessr.features.game.PlayerHistories;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetFriendsTodayHandler {

    private final FriendGroup friendGroup;
    private final PlayerHistories histories;

    @Transactional(readOnly = true)
    public FriendsTodayResponse handle(UUID userId) {
        LocalDate today = histories.today();
        FriendGroup.Group group = friendGroup.of(userId);
        Map<UUID, PlayerHistories.Record> records = histories.of(group.members());
        List<TodayStandings.Member> members = group.members().stream()
                .map(member -> new TodayStandings.Member(
                        group.usernames().get(member),
                        member.equals(userId),
                        records.get(member).plays(),
                        records.get(member).result().days()))
                .toList();
        TodayStandings.Member me = members.stream().filter(TodayStandings.Member::me).findFirst().orElseThrow();
        if (!TodayStandings.isUnlocked(me, today)) {
            return new FriendsTodayResponse(today, true, List.of());
        }
        return new FriendsTodayResponse(
                today,
                false,
                TodayStandings.rank(members, today).stream()
                        .map(standing -> new FriendsTodayResponse.Player(
                                standing.username(),
                                standing.me(),
                                standing.status().name(),
                                standing.points(),
                                standing.max(),
                                standing.rounds() == null
                                        ? null
                                        : standing.rounds().stream()
                                                .map(round -> new FriendsTodayResponse.Round(
                                                        round.position(), round.points()))
                                                .toList()))
                        .toList());
    }
}
