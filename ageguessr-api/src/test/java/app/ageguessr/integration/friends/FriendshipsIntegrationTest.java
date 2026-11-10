package app.ageguessr.integration.friends;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.features.friends.FriendInvite;
import app.ageguessr.features.friends.FriendInviteRepository;
import app.ageguessr.features.friends.FriendUsers;
import app.ageguessr.features.friends.Friendship;
import app.ageguessr.features.friends.FriendshipRepository;
import app.ageguessr.features.friends.FriendshipRepository.RelationRow;
import app.ageguessr.features.friends.FriendshipStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** The constraints, cascades and SQL of V18__create_friendships.sql, against a real Postgres. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class FriendshipsIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
    private static final AtomicInteger KEYS = new AtomicInteger();

    @Autowired
    private FriendshipRepository friendships;

    @Autowired
    private FriendInviteRepository invites;

    @Autowired
    private FriendUsers friendUsers;

    @Autowired
    private UserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void keepsOneRelationPerPairWhateverItsDirection() {
        User alice = saveUser(true);
        User bob = saveUser(true);
        friendships.saveAndFlush(Friendship.request(alice.getId(), bob.getId(), NOW));

        assertThatThrownBy(() -> friendships.saveAndFlush(Friendship.request(bob.getId(), alice.getId(), NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void refusesAFriendshipWithOneselfInTheDatabaseToo() {
        User alice = saveUser(true);

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO friendships (requester_id, addressee_id, status) VALUES (?, ?, 'PENDING')",
                        alice.getId(),
                        alice.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAcceptedFriendshipHasItsDate() {
        User alice = saveUser(true);
        User bob = saveUser(true);

        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO friendships (requester_id, addressee_id, status) VALUES (?, ?, 'ACCEPTED')",
                        alice.getId(),
                        bob.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAnAccountDeletesItsFriendshipsAndInvite() {
        User alice = saveUser(true);
        User bob = saveUser(true);
        friendships.saveAndFlush(Friendship.accepted(alice.getId(), bob.getId(), NOW));
        invites.saveAndFlush(new FriendInvite(alice.getId(), "ALICE000", NOW));
        entityManager.clear();

        jdbc.update("DELETE FROM users WHERE id = ?", alice.getId());

        assertThat(friendships.findFriendIdsOf(bob.getId())).isEmpty();
        assertThat(invites.findByCode("ALICE000")).isEmpty();
    }

    @Test
    void findsAVerifiedAccountByUsernameWhateverTheCase() {
        User verified = saveUser(true);
        User unverified = saveUser(false);

        assertThat(friendUsers.findVerifiedIdByUsername(verified.getUsername().toUpperCase()))
                .contains(verified.getId());
        assertThat(friendUsers.findVerifiedIdByUsername(unverified.getUsername())).isEmpty();
        assertThat(friendUsers.findVerifiedIdByUsername("nobody-here")).isEmpty();
    }

    @Test
    void readsTheRelationsOfAnAccountWithTheOtherUsername() {
        User me = saveUser(true);
        User friend = saveUser(true);
        User asker = saveUser(true);
        User asked = saveUser(true);
        friendships.saveAndFlush(Friendship.accepted(friend.getId(), me.getId(), NOW));
        friendships.saveAndFlush(Friendship.request(asker.getId(), me.getId(), NOW));
        friendships.saveAndFlush(Friendship.request(me.getId(), asked.getId(), NOW));
        friendships.saveAndFlush(Friendship.request(asker.getId(), asked.getId(), NOW));

        List<RelationRow> rows = friendships.findRelationsOf(me.getId());

        assertThat(rows)
                .extracting(RelationRow::getUsername, RelationRow::getStatus, RelationRow::getOutgoing)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(friend.getUsername(), "ACCEPTED", false),
                        org.assertj.core.groups.Tuple.tuple(asker.getUsername(), "PENDING", false),
                        org.assertj.core.groups.Tuple.tuple(asked.getUsername(), "PENDING", true));
        assertThat(friendships.findFriendIdsOf(me.getId())).containsExactly(friend.getId());
        assertThat(friendships.countFriendsOf(me.getId())).isEqualTo(1);
        assertThat(friendships.countByRequesterIdAndStatus(me.getId(), FriendshipStatus.PENDING))
                .isEqualTo(1);
        assertThat(friendships.findBetween(asked.getId(), me.getId())).isPresent();
    }

    @Test
    void readsUsernamesByIds() {
        User alice = saveUser(true);
        User bob = saveUser(true);

        assertThat(friendUsers.findUsernamesOf(List.of(alice.getId(), bob.getId(), UUID.randomUUID())))
                .containsOnly(
                        java.util.Map.entry(alice.getId(), alice.getUsername()),
                        java.util.Map.entry(bob.getId(), bob.getUsername()));
        assertThat(friendUsers.findUsernamesOf(List.of())).isEmpty();
        assertThat(friendUsers.findUsernameOf(alice.getId())).contains(alice.getUsername());
        assertThat(friendUsers.findUsernameOf(UUID.randomUUID())).isEmpty();
    }

    private User saveUser(boolean verified) {
        int key = KEYS.incrementAndGet();
        return users.saveAndFlush(User.builder()
                .email("friends" + key + "@x.com")
                .username("friends" + key + "x")
                .passwordHash("hash")
                .emailVerified(verified)
                .createdAt(Instant.now())
                .build());
    }
}
