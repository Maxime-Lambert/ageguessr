package app.ageguessr.features.friends.ListFriends;

import app.ageguessr.features.friends.FriendshipRepository;
import app.ageguessr.features.friends.FriendshipRepository.RelationRow;
import app.ageguessr.features.friends.FriendshipStatus;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Friends by username, requests newest first. */
@Service
@RequiredArgsConstructor
public class ListFriendsHandler {

    private final FriendshipRepository friendships;

    @Transactional(readOnly = true)
    public FriendsResponse handle(UUID userId) {
        List<RelationRow> rows = friendships.findRelationsOf(userId);
        Predicate<RelationRow> accepted = row -> FriendshipStatus.ACCEPTED.name().equals(row.getStatus());
        Comparator<RelationRow> newestFirst =
                Comparator.comparing(RelationRow::getSince).reversed();
        return new FriendsResponse(
                select(rows, accepted, Comparator.comparing(row -> row.getUsername().toLowerCase(Locale.ROOT))),
                select(rows, accepted.negate().and(row -> !row.getOutgoing()), newestFirst),
                select(rows, accepted.negate().and(RelationRow::getOutgoing), newestFirst));
    }

    private static List<FriendsResponse.Relation> select(
            List<RelationRow> rows, Predicate<RelationRow> filter, Comparator<RelationRow> order) {
        return rows.stream()
                .filter(filter)
                .sorted(order)
                .map(row -> new FriendsResponse.Relation(row.getId(), row.getUsername(), row.getSince()))
                .toList();
    }
}
