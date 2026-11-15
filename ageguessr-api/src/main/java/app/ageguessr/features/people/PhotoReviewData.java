package app.ageguessr.features.people;

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

/** The photos an admin reviewed (person_photos.reviewed_by), in their data export. Empty for a player. */
@Component
@Order(4)
@RequiredArgsConstructor
public class PhotoReviewData implements PersonalDataSection {

    private final NamedParameterJdbcTemplate jdbc;

    record Review(UUID photoId, Instant reviewedAt) {}

    @Override
    public String name() {
        return "photoReviews";
    }

    @Override
    public Set<String> tables() {
        return Set.of("person_photos");
    }

    @Override
    public List<Review> export(UUID userId) {
        return jdbc.query(
                "SELECT id, reviewed_at FROM person_photos WHERE reviewed_by = :id ORDER BY reviewed_at",
                Map.of("id", userId),
                (row, index) -> new Review(row.getObject("id", UUID.class), instant(row, "reviewed_at")));
    }
}
