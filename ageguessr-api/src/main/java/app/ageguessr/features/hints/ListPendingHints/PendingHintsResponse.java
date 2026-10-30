package app.ageguessr.features.hints.ListPendingHints;

import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import java.util.List;
import java.util.UUID;

/** The hints waiting for an admin: the translated ones. */
public record PendingHintsResponse(long totalPending, List<Item> items) {

    public record Item(
            UUID hintId,
            HintCategory category,
            int year,
            HintAudience audience,
            String textEn,
            String textFr,
            String sourceUrl,
            String sourceKind) {}
}
