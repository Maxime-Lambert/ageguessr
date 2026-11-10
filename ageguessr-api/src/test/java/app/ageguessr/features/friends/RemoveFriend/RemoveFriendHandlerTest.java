package app.ageguessr.features.friends.RemoveFriend;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RemoveFriendHandlerTest {

    private static final Instant SINCE = Instant.parse("2026-11-09T10:00:00Z");
    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final RemoveFriendHandler handler = new RemoveFriendHandler(friendships);
    private final UUID me = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID friendshipId = UUID.randomUUID();

    @Test
    void eitherFriendRemovesTheFriendship() {
        Friendship friendship = Friendship.accepted(bob, me, SINCE);
        when(friendships.findById(friendshipId)).thenReturn(Optional.of(friendship));

        handler.handle(me, friendshipId);
        handler.handle(bob, friendshipId);

        verify(friendships, org.mockito.Mockito.times(2)).delete(friendship);
    }

    @Test
    void aStrangerCannotRemoveIt() {
        when(friendships.findById(friendshipId))
                .thenReturn(Optional.of(Friendship.accepted(bob, UUID.randomUUID(), SINCE)));

        assertThatThrownBy(() -> handler.handle(me, friendshipId)).isInstanceOf(NotFoundException.class);
        verify(friendships, never()).delete(any());
    }

    @Test
    void aPendingRequestIsNotAFriend() {
        when(friendships.findById(friendshipId)).thenReturn(Optional.of(Friendship.request(bob, me, SINCE)));

        assertThatThrownBy(() -> handler.handle(me, friendshipId)).isInstanceOf(NotFoundException.class);
    }
}
