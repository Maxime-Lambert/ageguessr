package app.ageguessr.features.friends.DeleteFriendRequest;

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

class DeleteFriendRequestHandlerTest {

    private static final Instant SENT = Instant.parse("2026-11-09T10:00:00Z");
    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final DeleteFriendRequestHandler handler = new DeleteFriendRequestHandler(friendships);
    private final UUID me = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();

    @Test
    void theAddresseeDeclines() {
        Friendship request = Friendship.request(bob, me, SENT);
        when(friendships.findById(requestId)).thenReturn(Optional.of(request));

        handler.handle(me, requestId);

        verify(friendships).delete(request);
    }

    @Test
    void theSenderCancels() {
        Friendship request = Friendship.request(me, bob, SENT);
        when(friendships.findById(requestId)).thenReturn(Optional.of(request));

        handler.handle(me, requestId);

        verify(friendships).delete(request);
    }

    @Test
    void aStrangerCannotDeleteIt() {
        when(friendships.findById(requestId))
                .thenReturn(Optional.of(Friendship.request(bob, UUID.randomUUID(), SENT)));

        assertThatThrownBy(() -> handler.handle(me, requestId)).isInstanceOf(NotFoundException.class);
        verify(friendships, never()).delete(any());
    }

    @Test
    void doesNotRemoveAFriendship() {
        when(friendships.findById(requestId)).thenReturn(Optional.of(Friendship.accepted(bob, me, SENT)));

        assertThatThrownBy(() -> handler.handle(me, requestId)).isInstanceOf(NotFoundException.class);
        verify(friendships, never()).delete(any());
    }
}
