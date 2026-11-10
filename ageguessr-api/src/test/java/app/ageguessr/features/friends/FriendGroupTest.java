package app.ageguessr.features.friends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FriendGroupTest {

    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final FriendGroup friendGroup = new FriendGroup(friendships);

    private record Name(UUID getId, String getUsername) implements FriendshipRepository.UsernameRow {}

    @Test
    void theCallerFirstThenTheFriendsWithTheirUsernames() {
        UUID me = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        when(friendships.findFriendIdsOf(me)).thenReturn(List.of(bob, gone));
        when(friendships.findUsernamesOf(anyList())).thenReturn(List.of(new Name(bob, "bob"), new Name(me, "me")));

        FriendGroup.Group group = friendGroup.of(me);

        assertThat(group.members()).containsExactly(me, bob);
        assertThat(group.usernames()).containsEntry(me, "me").containsEntry(bob, "bob").doesNotContainKey(gone);
    }
}
