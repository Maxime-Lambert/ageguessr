package app.ageguessr.features.auth;

import static app.ageguessr.shared.personaldata.Rows.instant;

import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.personaldata.PersonalDataSection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The account, its sessions and its email links in the data export. Dates only for the
 * sessions and links: their hashes, like the password's, are secrets and never leave.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class AccountData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;

    record Account(
            String email,
            String username,
            String language,
            String role,
            boolean emailVerified,
            Instant createdAt,
            String termsVersion,
            Instant termsAcceptedAt,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant lastActiveAt,
            Instant inactivityWarnedAt) {}

    record Session(Instant createdAt, Instant expiresAt, Instant revokedAt) {}

    record EmailLink(String purpose, Instant createdAt, Instant expiresAt, Instant usedAt) {}

    record Export(Account account, List<Session> sessions, List<EmailLink> emailLinks) {}

    @Override
    public String name() {
        return "account";
    }

    @Override
    public Set<String> tables() {
        return Set.of("users", "refresh_tokens", "email_verification_tokens", "account_tokens");
    }

    @Override
    public Export export(UUID userId) {
        Map<String, UUID> id = Map.of("id", userId);
        Account account = jdbc.query(
                """
                SELECT email, username, language, role, email_verified, created_at, terms_version,
                       terms_accepted_at, failed_login_attempts, locked_until, last_active_at,
                       inactivity_warned_at
                FROM users WHERE id = :id
                """,
                id,
                (row, index) -> new Account(
                        row.getString("email"),
                        row.getString("username"),
                        row.getString("language"),
                        row.getString("role"),
                        row.getBoolean("email_verified"),
                        instant(row, "created_at"),
                        row.getString("terms_version"),
                        instant(row, "terms_accepted_at"),
                        row.getInt("failed_login_attempts"),
                        instant(row, "locked_until"),
                        instant(row, "last_active_at"),
                        instant(row, "inactivity_warned_at")))
                .stream()
                .findFirst()
                // Deleted since the token was issued.
                .orElseThrow(() -> new NotFoundException("User not found"));
        List<Session> sessions = jdbc.query(
                "SELECT created_at, expires_at, revoked_at FROM refresh_tokens WHERE user_id = :id ORDER BY created_at",
                id,
                (row, index) -> new Session(
                        instant(row, "created_at"), instant(row, "expires_at"), instant(row, "revoked_at")));
        List<EmailLink> links = jdbc.query(
                """
                SELECT 'EMAIL_VERIFICATION' AS purpose, created_at, expires_at, consumed_at
                FROM email_verification_tokens WHERE user_id = :id
                UNION ALL
                SELECT purpose, created_at, expires_at, consumed_at FROM account_tokens WHERE user_id = :id
                ORDER BY created_at
                """,
                id,
                (row, index) -> new EmailLink(
                        row.getString("purpose"),
                        instant(row, "created_at"),
                        instant(row, "expires_at"),
                        instant(row, "consumed_at")));
        return new Export(account, sessions, links);
    }
}
