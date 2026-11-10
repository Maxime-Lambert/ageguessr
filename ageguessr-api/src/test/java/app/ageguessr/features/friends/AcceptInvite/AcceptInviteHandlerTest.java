package app.ageguessr.features.friends.AcceptInvite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.ageguessr.features.friends.FriendErrors;
import app.ageguessr.features.friends.FriendInvite;
import app.ageguessr.features.friends.FriendInviteRepository;
import app.ageguessr.features.friends.FriendLimits;
import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class AcceptInviteHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private final FriendInviteRepository invites = mock(FriendInviteRepository.class);
    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final FriendLimits limits = mock(FriendLimits.class);
    private final AcceptInviteHandler handler =
            new AcceptInviteHandler(invites, friendships, limits, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID me = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void ownerHasACode() {
        when(invites.findByCode("K7F2QX9M")).thenReturn(Optional.of(new FriendInvite(owner, "K7F2QX9M", NOW)));
    }

    @Test
    void makesTheTwoFriendsAtOnce() {
        handler.handle(me, " k7f2qx9m ");

        ArgumentCaptor<Friendship> saved = ArgumentCaptor.forClass(Friendship.class);
        verify(friendships).saveAndFlush(saved.capture());
        assertThat(saved.getValue().isAccepted()).isTrue();
        assertThat(saved.getValue().involves(me)).isTrue();
        assertThat(saved.getValue().involves(owner)).isTrue();
        verify(limits).checkCanBecomeFriends(me, owner);
    }

    @Test
    void anUnknownCodeIsNotFound() {
        assertThatThrownBy(() -> handler.handle(me, "ZZZZZZZZ"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("This invite link no longer works");
    }

    @Test
    void refusesOnesOwnLink() {
        assertThatThrownBy(() -> handler.handle(owner, "K7F2QX9M"))
                .hasFieldOrPropertyWithValue("errorCode", "OWN_INVITE");
    }

    @Test
    void alreadyFriendsIsNothingToDo() {
        when(friendships.findBetween(me, owner)).thenReturn(Optional.of(Friendship.accepted(owner, me, NOW)));

        handler.handle(me, "K7F2QX9M");

        verify(friendships, never()).saveAndFlush(any());
        verifyNoInteractions(limits);
    }

    @Test
    void acceptsARequestWaitingEitherWay() {
        Friendship mine = Friendship.request(me, owner, NOW);
        when(friendships.findBetween(me, owner)).thenReturn(Optional.of(mine));

        handler.handle(me, "K7F2QX9M");

        assertThat(mine.isAccepted()).isTrue();
        verify(friendships, never()).saveAndFlush(any());
    }

    @Test
    void checksTheLimits() {
        doThrow(FriendErrors.otherFriendLimitReached()).when(limits).checkCanBecomeFriends(me, owner);

        assertThatThrownBy(() -> handler.handle(me, "K7F2QX9M"))
                .hasFieldOrPropertyWithValue("errorCode", "OTHER_FRIEND_LIMIT_REACHED");
        verify(friendships, never()).saveAndFlush(any());
    }

    @Test
    void aRequestSentAtTheSameInstantIsAConflict() {
        when(friendships.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("pair"));

        assertThatThrownBy(() -> handler.handle(me, "K7F2QX9M"))
                .hasFieldOrPropertyWithValue("errorCode", "REQUEST_ALREADY_SENT");
    }
}
