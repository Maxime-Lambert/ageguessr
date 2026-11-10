package app.ageguessr.features.friends;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What friends read of the accounts: an id and a username, never an email. SQL on `users`
 * rather than the auth feature's repository, which friends never imports
 * (ArchitectureTests), as the challenge leaderboard does. Read at each request, so a
 * renamed account shows its new name.
 */
@Component
@RequiredArgsConstructor
public class FriendUsers {

    private final NamedParameterJdbcTemplate jdbc;

    /**
     * The account a username names, whatever its case (as the unique index), if its email
     * is verified: an account that cannot sign in is not a friend to add.
     */
    public Optional<UUID> findVerifiedIdByUsername(String username) {
        return jdbc
                .queryForList(
                        "SELECT id FROM users WHERE LOWER(username) = LOWER(:username) AND email_verified",
                        Map.of("username", username),
                        UUID.class)
                .stream()
                .findFirst();
    }

    public Optional<String> findUsernameOf(UUID userId) {
        return jdbc
                .queryForList("SELECT username FROM users WHERE id = :id", Map.of("id", userId), String.class)
                .stream()
                .findFirst();
    }

    /** @return the username of each account still there, by id */
    public Map<UUID, String> findUsernamesOf(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .query(
                        "SELECT id, username FROM users WHERE id IN (:ids)",
                        Map.of("ids", userIds),
                        (row, index) -> Map.entry(row.getObject("id", UUID.class), row.getString("username")))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
