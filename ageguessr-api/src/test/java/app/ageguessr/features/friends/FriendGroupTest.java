package app.ageguessr.features.friends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FriendGroupTest {

    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final FriendUsers users = mock(FriendUsers.class);
    private final FriendGroup friendGroup = new FriendGroup(friendships, users);

    @Test
    void theCallerFirstThenTheFriendsWithTheirUsernames() {
        UUID me = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        when(friendships.findFriendIdsOf(me)).thenReturn(List.of(bob, gone));
        when(users.findUsernamesOf(anyList())).thenReturn(Map.of(bob, "bob", me, "me"));

        FriendGroup.Group group = friendGroup.of(me);

        assertThat(group.members()).containsExactly(me, bob);
        assertThat(group.usernames()).containsEntry(me, "me").containsEntry(bob, "bob").doesNotContainKey(gone);
    }
}
