package app.ageguessr.features.friends.AcceptFriendRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.friends.FriendErrors;
import app.ageguessr.features.friends.FriendLimits;
import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcceptFriendRequestHandlerTest {

    private static final Instant SENT = Instant.parse("2026-11-09T10:00:00Z");
    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final FriendLimits limits = mock(FriendLimits.class);
    private final AcceptFriendRequestHandler handler =
            new AcceptFriendRequestHandler(friendships, limits, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID me = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();

    @Test
    void theAddresseeAccepts() {
        Friendship request = Friendship.request(bob, me, SENT);
        when(friendships.findById(requestId)).thenReturn(Optional.of(request));

        handler.handle(me, requestId);

        assertThat(request.isAccepted()).isTrue();
        assertThat(request.getAcceptedAt()).isEqualTo(NOW);
        verify(limits).checkCanBecomeFriends(me, bob);
    }

    @Test
    void theSenderCannotAcceptTheirOwnRequest() {
        when(friendships.findById(requestId)).thenReturn(Optional.of(Friendship.request(me, bob, SENT)));

        assertThatThrownBy(() -> handler.handle(me, requestId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aStrangerGetsTheSameNotFound() {
        when(friendships.findById(requestId))
                .thenReturn(Optional.of(Friendship.request(bob, UUID.randomUUID(), SENT)));

        assertThatThrownBy(() -> handler.handle(me, requestId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aCancelledRequestIsNotFound() {
        assertThatThrownBy(() -> handler.handle(me, requestId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void anAcceptedFriendshipIsNotARequest() {
        when(friendships.findById(requestId)).thenReturn(Optional.of(Friendship.accepted(bob, me, SENT)));

        assertThatThrownBy(() -> handler.handle(me, requestId)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void staysPendingWhenALimitIsReached() {
        Friendship request = Friendship.request(bob, me, SENT);
        when(friendships.findById(requestId)).thenReturn(Optional.of(request));
        doThrow(FriendErrors.friendLimitReached()).when(limits).checkCanBecomeFriends(me, bob);

        assertThatThrownBy(() -> handler.handle(me, requestId))
                .hasFieldOrPropertyWithValue("errorCode", "FRIEND_LIMIT_REACHED");
        assertThat(request.isAccepted()).isFalse();
    }
}
