package app.ageguessr.features.friends.ListFriends;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The caller's friends and requests. Each row carries the id of the relation, which the
 * actions take, and the other account's username: never its id nor its email.
 */
public record FriendsResponse(List<Relation> friends, List<Relation> incoming, List<Relation> outgoing) {

    /** @param since when the friendship was accepted, or when the request was sent */
    public record Relation(UUID id, String username, Instant since) {}
}
