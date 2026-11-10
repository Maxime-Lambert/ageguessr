package app.ageguessr.features.friends.SendFriendRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.friends.FriendErrors;
import app.ageguessr.features.friends.FriendLimits;
import app.ageguessr.features.friends.FriendUsers;
import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

class SendFriendRequestHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final FriendLimits limits = mock(FriendLimits.class);
    private final FriendUsers users = mock(FriendUsers.class);
    private final SendFriendRequestHandler handler =
            new SendFriendRequestHandler(friendships, users, limits, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID me = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void sendsARequestToTheAccountOfTheUsername() {
        when(users.findVerifiedIdByUsername("Bob")).thenReturn(Optional.of(bob));

        handler.handle(me, new SendFriendRequestCommand("Bob"));

        ArgumentCaptor<Friendship> saved = ArgumentCaptor.forClass(Friendship.class);
        verify(friendships).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getRequesterId()).isEqualTo(me);
        assertThat(saved.getValue().getAddresseeId()).isEqualTo(bob);
        assertThat(saved.getValue().isAccepted()).isFalse();
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
        verify(limits).checkCanSendRequest(me);
    }

    @Test
    void anUnknownUsernameIsNotFound() {
        assertThatThrownBy(() -> handler.handle(me, new SendFriendRequestCommand("nobody")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No player with this username");
    }

    @Test
    void oneselfIsNotFoundEither() {
        when(users.findVerifiedIdByUsername("me")).thenReturn(Optional.of(me));

        assertThatThrownBy(() -> handler.handle(me, new SendFriendRequestCommand("me")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No player with this username");
        verify(friendships, never()).saveAndFlush(any());
    }

    @Test
    void refusesWhenAlreadyFriends() {
        when(users.findVerifiedIdByUsername("Bob")).thenReturn(Optional.of(bob));
        when(friendships.findBetween(me, bob)).thenReturn(Optional.of(Friendship.accepted(bob, me, NOW)));

        assertThatThrownBy(() -> handler.handle(me, new SendFriendRequestCommand("Bob")))
                .hasFieldOrPropertyWithValue("errorCode", "ALREADY_FRIENDS");
    }

    @Test
    void refusesASecondRequest() {
        when(users.findVerifiedIdByUsername("Bob")).thenReturn(Optional.of(bob));
        when(friendships.findBetween(me, bob)).thenReturn(Optional.of(Friendship.request(me, bob, NOW)));

        assertThatThrownBy(() -> handler.handle(me, new SendFriendRequestCommand("Bob")))
                .hasFieldOrPropertyWithValue("errorCode", "REQUEST_ALREADY_SENT");
    }

    @Test
    void neverTurnsACrossingRequestIntoAFriendship() {
        Friendship incoming = Friendship.request(bob, me, NOW);
        when(users.findVerifiedIdByUsername("Bob")).thenReturn(Optional.of(bob));
        when(friendships.findBetween(me, bob)).thenReturn(Optional.of(incoming));

        assertThatThrownBy(() -> handler.handle(me, new SendFriendRequestCommand("Bob")))
                .hasFieldOrPropertyWithValue("errorCode", "INCOMING_REQUEST_EXISTS");
        assertThat(incoming.isAccepted()).isFalse();
        verify(friendships, never()).saveAndFlush(any());
    }

    @Test
    void checksTheLimitsBeforeSaving() {
        when(users.findVerifiedIdByUsername("Bob")).thenReturn(Optional.of(bob));
        doThrow(FriendErrors.pendingLimitReached()).when(limits).checkCanSendRequest(me);

        assertThatThrownBy(() -> handler.handle(me, new SendFriendRequestCommand("Bob")))
                .isInstanceOf(ConflictException.class);
        verify(friendships, never()).saveAndFlush(any());
    }

    @Test
    void aRequestCrossingAtTheSameInstantIsAConflict() {
        when(users.findVerifiedIdByUsername("Bob")).thenReturn(Optional.of(bob));
        when(friendships.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("pair"));

        assertThatThrownBy(() -> handler.handle(me, new SendFriendRequestCommand("Bob")))
                .hasFieldOrPropertyWithValue("errorCode", "REQUEST_ALREADY_SENT");
    }
}
