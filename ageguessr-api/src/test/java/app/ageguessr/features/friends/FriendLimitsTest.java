package app.ageguessr.features.friends;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.ageguessr.shared.exceptions.ConflictException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FriendLimitsTest {

    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final FriendLimits limits = new FriendLimits(friendships, new FriendsProperties(3, 2));
    private final UUID me = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test
    void letsTwoAccountsBelowTheLimitBecomeFriends() {
        when(friendships.countFriendsOf(me)).thenReturn(2L);
        when(friendships.countFriendsOf(other)).thenReturn(2L);

        assertThatCode(() -> limits.checkCanBecomeFriends(me, other)).doesNotThrowAnyException();
    }

    @Test
    void refusesWhenTheCallerIsFull() {
        when(friendships.countFriendsOf(me)).thenReturn(3L);

        assertThatThrownBy(() -> limits.checkCanBecomeFriends(me, other))
                .isInstanceOf(ConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", "FRIEND_LIMIT_REACHED");
    }

    @Test
    void refusesWhenTheOtherIsFull() {
        when(friendships.countFriendsOf(other)).thenReturn(3L);

        assertThatThrownBy(() -> limits.checkCanBecomeFriends(me, other))
                .hasFieldOrPropertyWithValue("errorCode", "OTHER_FRIEND_LIMIT_REACHED");
    }

    @Test
    void letsASenderBelowBothLimitsSend() {
        when(friendships.countFriendsOf(me)).thenReturn(2L);
        when(friendships.countByRequesterIdAndStatus(me, FriendshipStatus.PENDING)).thenReturn(1L);

        assertThatCode(() -> limits.checkCanSendRequest(me)).doesNotThrowAnyException();
    }

    @Test
    void refusesAFullSender() {
        when(friendships.countFriendsOf(me)).thenReturn(3L);

        assertThatThrownBy(() -> limits.checkCanSendRequest(me))
                .hasFieldOrPropertyWithValue("errorCode", "FRIEND_LIMIT_REACHED");
    }

    @Test
    void refusesTooManyPendingRequests() {
        when(friendships.countByRequesterIdAndStatus(me, FriendshipStatus.PENDING)).thenReturn(2L);

        assertThatThrownBy(() -> limits.checkCanSendRequest(me))
                .hasFieldOrPropertyWithValue("errorCode", "PENDING_LIMIT_REACHED");
    }
}
