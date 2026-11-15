package app.ageguessr.features.friends;

import static app.ageguessr.shared.personaldata.Rows.instant;

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
 * The friendships of a player in the data export, requests included: the other player by
 * username only (never their email, as everywhere in friends), who asked, and when.
 */
@Component
@Order(3)
@RequiredArgsConstructor
public class FriendData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;

    /** @param requestSent true when this player sent the request, false when they received it */
    record Friendship(String username, String status, boolean requestSent, Instant requestedAt, Instant acceptedAt) {}

    @Override
    public String name() {
        return "friends";
    }

    @Override
    public Set<String> tables() {
        return Set.of("friendships");
    }

    @Override
    public List<Friendship> export(UUID userId) {
        return jdbc.query(
                """
                SELECT other.username, f.status, f.requester_id = :id AS request_sent, f.created_at, f.accepted_at
                FROM friendships f
                JOIN users other
                  ON other.id = CASE WHEN f.requester_id = :id THEN f.addressee_id ELSE f.requester_id END
                WHERE :id IN (f.requester_id, f.addressee_id)
                ORDER BY f.created_at
                """,
                Map.of("id", userId),
                (row, index) -> new Friendship(
                        row.getString("username"),
                        row.getString("status"),
                        row.getBoolean("request_sent"),
                        instant(row, "created_at"),
                        instant(row, "accepted_at")));
    }
}
