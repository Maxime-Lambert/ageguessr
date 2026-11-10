package app.ageguessr.features.friends.PreviewInvite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.ageguessr.features.friends.FriendInvite;
import app.ageguessr.features.friends.FriendInviteRepository;
import app.ageguessr.features.friends.FriendUsers;
import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreviewInviteHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private final FriendInviteRepository invites = mock(FriendInviteRepository.class);
    private final FriendshipRepository friendships = mock(FriendshipRepository.class);
    private final FriendUsers users = mock(FriendUsers.class);
    private final PreviewInviteHandler handler = new PreviewInviteHandler(invites, friendships, users);
    private final UUID me = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void ownerHasACode() {
        when(invites.findByCode("K7F2QX9M")).thenReturn(Optional.of(new FriendInvite(owner, "K7F2QX9M", NOW)));
        when(users.findUsernameOf(owner)).thenReturn(Optional.of("Holmes"));
    }

    @Test
    void showsWhoSentTheLink() {
        assertThat(handler.handle(me, "k7f2qx9m")).isEqualTo(new InvitePreviewResponse("Holmes", false, false));
    }

    @Test
    void tellsTheOwnerItIsTheirs() {
        assertThat(handler.handle(owner, "K7F2QX9M")).isEqualTo(new InvitePreviewResponse("Holmes", true, false));
    }

    @Test
    void tellsWhenAlreadyFriends() {
        when(friendships.findBetween(me, owner)).thenReturn(Optional.of(Friendship.accepted(owner, me, NOW)));

        assertThat(handler.handle(me, "K7F2QX9M").alreadyFriends()).isTrue();
    }

    @Test
    void aPendingRequestIsNotYetFriends() {
        when(friendships.findBetween(me, owner)).thenReturn(Optional.of(Friendship.request(owner, me, NOW)));

        assertThat(handler.handle(me, "K7F2QX9M").alreadyFriends()).isFalse();
    }

    @Test
    void anUnknownCodeIsNotFound() {
        assertThatThrownBy(() -> handler.handle(me, "ZZZZZZZZ")).isInstanceOf(NotFoundException.class);
    }
}
