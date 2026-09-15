package app.ageguessr.features.auth;

import jakarta.persistence.Entity;
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
 * "foo@x.com" both exist as separate accounts.
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

    private String passwordHash;

    private boolean emailVerified;

    private int failedLoginAttempts;

    private Instant lockedUntil;

    private Instant createdAt;
}
