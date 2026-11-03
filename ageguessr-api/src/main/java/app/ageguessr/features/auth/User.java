package app.ageguessr.features.auth;

import app.ageguessr.shared.i18n.Language;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Uniqueness on email is enforced in the database via a case-insensitive index on
 * LOWER(email) (see V1__create_auth_tables.sql), not via a JPA @Column(unique=true) —
 * a plain unique constraint is case-sensitive in Postgres and would let "Foo@x.com" and
 * "foo@x.com" both exist as separate accounts. Same for username (LOWER(username), see
 * V2__add_username_and_account_tokens.sql), whose display casing is kept as typed.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String email;

    private String username;

    private String passwordHash;

    private boolean emailVerified;

    private int failedLoginAttempts;

    private Instant lockedUntil;

    private Instant createdAt;

    /** Null for accounts created before the Terms existed (see V3__add_terms_acceptance.sql). */
    private String termsVersion;

    private Instant termsAcceptedAt;

    /** Defaults to USER for every account created in code, like the column default. */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private UserRole role = UserRole.USER;

    /** Of the emails, and of the site once signed in. Null until chosen (V14): emails in English. */
    @Enumerated(EnumType.STRING)
    private Language language;

    /** Whether the account accepted the Terms in force: otherwise the site asks again. */
    public boolean hasAcceptedCurrentTerms() {
        return TermsVersion.CURRENT.equals(termsVersion);
    }

    /**
     * Records the acceptance of a version of the Terms. Accepting the same version again
     * keeps the first date: it is the proof of when the player agreed.
     */
    public void acceptTerms(String version, Instant now) {
        if (!TermsVersion.CURRENT.equals(version)) {
            throw new IllegalArgumentException("Only the Terms in force can be accepted");
        }
        if (version.equals(termsVersion)) {
            return;
        }
        termsVersion = version;
        termsAcceptedAt = now;
    }

    /** The language of the emails and their links: English while the account never chose one. */
    public Language languageOrDefault() {
        return Language.orDefault(language);
    }
}
