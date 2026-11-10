package app.ageguessr.features.friends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.game.ShareCodes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FriendInviteCodesTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private final FriendInviteRepository invites = mock(FriendInviteRepository.class);
    private final ShareCodes shareCodes = mock(ShareCodes.class);
    private final FriendInviteCodes codes =
            new FriendInviteCodes(invites, shareCodes, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID me = UUID.randomUUID();

    @BeforeEach
    void saveReturnsItsArgument() {
        when(invites.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void keepsAnExistingCode() {
        when(invites.findById(me)).thenReturn(Optional.of(new FriendInvite(me, "AAAA1111", NOW)));

        assertThat(codes.inviteOf(me).getCode()).isEqualTo("AAAA1111");
        verify(invites, never()).save(any());
    }

    @Test
    void createsTheCodeOnTheFirstRead() {
        when(shareCodes.next()).thenReturn("BBBB2222");

        FriendInvite invite = codes.inviteOf(me);

        assertThat(invite.getCode()).isEqualTo("BBBB2222");
        assertThat(invite.getUserId()).isEqualTo(me);
        assertThat(invite.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void replacesTheCode() {
        when(invites.findById(me)).thenReturn(Optional.of(new FriendInvite(me, "AAAA1111", Instant.EPOCH)));
        when(shareCodes.next()).thenReturn("CCCC3333");

        FriendInvite invite = codes.replace(me);

        assertThat(invite.getCode()).isEqualTo("CCCC3333");
        assertThat(invite.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void replacingWithoutACodeCreatesOne() {
        when(shareCodes.next()).thenReturn("DDDD4444");

        assertThat(codes.replace(me).getCode()).isEqualTo("DDDD4444");
    }

    @Test
    void skipsACodeAlreadyTaken() {
        when(shareCodes.next()).thenReturn("TAKEN000", "FREE0000");
        when(invites.existsByCode("TAKEN000")).thenReturn(true);

        assertThat(codes.inviteOf(me).getCode()).isEqualTo("FREE0000");
    }

    @Test
    void givesUpAfterThreeCollisions() {
        when(shareCodes.next()).thenReturn("TAKEN000");
        when(invites.existsByCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> codes.inviteOf(me)).isInstanceOf(IllegalStateException.class);
    }
}
