package app.ageguessr.features.hints;

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

/** The hints an admin reviewed (hints.reviewed_by), in their data export. Empty for a player. */
@Component
@Order(5)
@RequiredArgsConstructor
public class HintReviewData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;

    record Review(UUID hintId, Instant reviewedAt) {}

    @Override
    public String name() {
        return "hintReviews";
    }

    @Override
    public Set<String> tables() {
        return Set.of("hints");
    }

    @Override
    public List<Review> export(UUID userId) {
        return jdbc.query(
                "SELECT id, reviewed_at FROM hints WHERE reviewed_by = :id ORDER BY reviewed_at",
                Map.of("id", userId),
                (row, index) -> new Review(row.getObject("id", UUID.class), instant(row, "reviewed_at")));
    }
}
