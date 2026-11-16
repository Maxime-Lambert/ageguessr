package app.ageguessr.integration.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.TestcontainersConfiguration;
import app.ageguessr.features.auth.PurgeAccounts.PurgeAccountsHandler;
import app.ageguessr.functional.auth.AuthFunctionalTestConfig;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The retention of the accounts against the real database (7 days unverified, 3 years
 * inactive with 30 days of notice, as in prod). Other tests share the database: each
 * check reads its own accounts, never a count. 1096 days: never short of 3 calendar years.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, AuthFunctionalTestConfig.class})
class PurgeAccountsIntegrationTest {

    private static final Duration INACTIVE = Duration.ofDays(1096);
    private static final Duration NOTICE = Duration.ofDays(30);
    private static final Duration HOUR = Duration.ofHours(1);

    @Autowired
    private PurgeAccountsHandler handler;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingEmailSender emails;

    @Autowired
    private Clock clock;

    private record Account(UUID id, String email) {}

    private Account account(boolean verified, String role, Instant createdAt, Instant lastActiveAt, Instant warnedAt) {
        String email = "retention-" + UUID.randomUUID() + "@x.com";
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO users (email, username, password_hash, email_verified, role, created_at, last_active_at,
                                   inactivity_warned_at)
                VALUES (?, ?, 'hash', ?, ?, ?, ?, ?) RETURNING id
                """,
                UUID.class,
                email,
                "r" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
                verified,
                role,
                Timestamp.from(createdAt),
                Timestamp.from(lastActiveAt),
                warnedAt == null ? null : Timestamp.from(warnedAt));
        return new Account(id, email);
    }

    private Account player(Instant lastActiveAt, Instant warnedAt) {
        return account(true, "USER", lastActiveAt, lastActiveAt, warnedAt);
    }

    private boolean exists(Account account) {
        return jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, account.id()) == 1;
    }

    private Instant warnedAt(Account account) {
        Timestamp value =
                jdbc.queryForObject("SELECT inactivity_warned_at FROM users WHERE id = ?", Timestamp.class, account.id());
        return value == null ? null : value.toInstant();
    }

    @Test
    void deletesTheAccountsNeverVerifiedAfterSevenDays() {
        Instant now = clock.instant();
        Account eightDays = account(false, "USER", now.minus(Duration.ofDays(8)), now, null);
        Account sixDays = account(false, "USER", now.minus(Duration.ofDays(6)), now, null);
        Account verifiedLongAgo = account(true, "USER", now.minus(Duration.ofDays(8)), now, null);

        handler.purge();

        assertThat(exists(eightDays)).isFalse();
        assertThat(exists(sixDays)).isTrue();
        assertThat(exists(verifiedLongAgo)).isTrue();
    }

    @Test
    void warnsOnceThirtyDaysBeforeThreeYearsOfInactivity() {
        Instant now = clock.instant();
        Account due = player(now.minus(INACTIVE).plus(NOTICE).minus(HOUR), null);
        Account notYet = player(now.minus(INACTIVE).plus(NOTICE).plus(HOUR), null);

        handler.purge();

        assertThat(warnedAt(due)).isNotNull();
        assertThat(emails.lastMessageTo(due.email()).subject()).startsWith("Your AgeGuessr account will be deleted on");
        assertThat(warnedAt(notYet)).isNull();
        assertThat(emails.lastMessageTo(notYet.email())).isNull();
        assertThat(exists(due)).isTrue();

        Instant firstWarning = warnedAt(due);
        handler.purge();
        assertThat(warnedAt(due)).isEqualTo(firstWarning);
    }

    @Test
    void deletesAnInactiveAccountOnlyThirtyDaysAfterItsWarning() {
        Instant now = clock.instant();
        Account noticeServed = player(now.minus(INACTIVE).minus(HOUR), now.minus(NOTICE).minus(Duration.ofDays(1)));
        // Warned 30 days ago, give or take the run's start: deleted tomorrow, as announced.
        Account noticeAlmostServed = player(now.minus(INACTIVE).minus(HOUR), now.minus(NOTICE).minus(HOUR));
        // The job was down: three years are up, but the warning is only a day old.
        Account warnedLate = player(now.minus(INACTIVE).minus(Duration.ofDays(40)), now.minus(Duration.ofDays(1)));
        // Three years are up and never warned (warned today, deleted in 30 days).
        Account neverWarned = player(now.minus(INACTIVE).minus(HOUR), null);

        handler.purge();

        assertThat(exists(noticeServed)).isFalse();
        assertThat(exists(noticeAlmostServed)).isTrue();
        assertThat(exists(warnedLate)).isTrue();
        assertThat(exists(neverWarned)).isTrue();
        assertThat(warnedAt(neverWarned)).isNotNull();
    }

    @Test
    void neverPurgesAnAdmin() {
        Instant longAgo = clock.instant().minus(INACTIVE).minus(Duration.ofDays(60));
        Account admin = account(true, "ADMIN", longAgo, longAgo, longAgo);

        handler.purge();

        assertThat(exists(admin)).isTrue();
        assertThat(emails.lastMessageTo(admin.email())).isNull();
    }

    @Test
    void deletingAnAccountTakesItsGamesAndFriendshipsAlong() {
        Instant now = clock.instant();
        Account gone = player(now.minus(INACTIVE).minus(HOUR), now.minus(NOTICE).minus(Duration.ofDays(1)));
        Account friend = player(now, null);
        jdbc.update(
                "INSERT INTO friendships (requester_id, addressee_id, status, accepted_at) VALUES (?, ?, 'ACCEPTED', now())",
                gone.id(), friend.id());

        handler.purge();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM friendships WHERE ? IN (requester_id, addressee_id)", Integer.class, gone.id()))
                .isZero();
        assertThat(exists(friend)).isTrue();
    }
}
