package app.ageguessr.features.friends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FriendshipTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-11T10:00:00Z");
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void aRequestWaitsUntilAccepted() {
        Friendship request = Friendship.request(alice, bob, NOW);

        assertThat(request.isAccepted()).isFalse();
        assertThat(request.getAcceptedAt()).isNull();
        request.accept(LATER);
        assertThat(request.isAccepted()).isTrue();
        assertThat(request.getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(request.getAcceptedAt()).isEqualTo(LATER);
    }

    @Test
    void acceptingTwiceKeepsTheFirstDate() {
        Friendship request = Friendship.request(alice, bob, NOW);
        request.accept(NOW);
        request.accept(LATER);

        assertThat(request.getAcceptedAt()).isEqualTo(NOW);
    }

    @Test
    void canBeCreatedAlreadyAccepted() {
        Friendship friendship = Friendship.accepted(alice, bob, NOW);

        assertThat(friendship.isAccepted()).isTrue();
        assertThat(friendship.getAcceptedAt()).isEqualTo(NOW);
        assertThat(friendship.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void refusesOneselfAsAFriend() {
        assertThatThrownBy(() -> Friendship.request(alice, alice, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Friendship.accepted(alice, alice, NOW)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void seesTheOtherAccountFromEitherSide() {
        Friendship friendship = Friendship.request(alice, bob, NOW);

        assertThat(friendship.involves(alice)).isTrue();
        assertThat(friendship.involves(bob)).isTrue();
        assertThat(friendship.involves(UUID.randomUUID())).isFalse();
        assertThat(friendship.otherThan(alice)).isEqualTo(bob);
        assertThat(friendship.otherThan(bob)).isEqualTo(alice);
        assertThatThrownBy(() -> friendship.otherThan(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
