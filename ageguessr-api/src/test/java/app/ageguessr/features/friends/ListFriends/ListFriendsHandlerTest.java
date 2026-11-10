package app.ageguessr.features.friends.ListFriends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.ageguessr.features.friends.FriendshipRepository;
import app.ageguessr.features.friends.FriendshipRepository.RelationRow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ListFriendsHandlerTest {

    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final ListFriendsHandler handler = new ListFriendsHandler(friendships);
    private final UUID me = UUID.randomUUID();

    private record Row(UUID getId, String getStatus, boolean getOutgoing, String getUsername, Instant getSince)
            implements RelationRow {}

    private static Row row(String status, boolean outgoing, String username, String since) {
        return new Row(UUID.randomUUID(), status, outgoing, username, Instant.parse(since));
    }

    @Test
    void splitsFriendsIncomingAndOutgoing() {
        Row watson = row("ACCEPTED", true, "watson", "2026-11-01T00:00:00Z");
        Row adler = row("ACCEPTED", false, "Adler", "2026-11-05T00:00:00Z");
        Row oldIn = row("PENDING", false, "lestrade", "2026-11-02T00:00:00Z");
        Row newIn = row("PENDING", false, "hudson", "2026-11-08T00:00:00Z");
        Row out = row("PENDING", true, "moriarty", "2026-11-03T00:00:00Z");
        when(friendships.findRelationsOf(me)).thenReturn(List.of(watson, oldIn, out, adler, newIn));

        FriendsResponse response = handler.handle(me);

        assertThat(response.friends()).extracting(FriendsResponse.Relation::username)
                .containsExactly("Adler", "watson");
        assertThat(response.incoming()).extracting(FriendsResponse.Relation::username)
                .containsExactly("hudson", "lestrade");
        assertThat(response.outgoing()).extracting(FriendsResponse.Relation::username)
                .containsExactly("moriarty");
        assertThat(response.friends().getFirst().id()).isEqualTo(adler.getId());
        assertThat(response.friends().getFirst().since()).isEqualTo(adler.getSince());
    }

    @Test
    void emptyWithoutAnyRelation() {
        assertThat(handler.handle(me)).isEqualTo(new FriendsResponse(List.of(), List.of(), List.of()));
    }
}
