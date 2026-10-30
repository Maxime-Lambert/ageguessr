package app.ageguessr.features.hints.ListPendingHints;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** How many pending hints to return: a small batch, the page fetches more as it goes. */
public record ListPendingHintsQuery(@Min(1) @Max(50) Integer limit) {

    static final int DEFAULT_LIMIT = 20;

    public ListPendingHintsQuery {
        // An absent ?limit binds to null; the default applies before validation runs.
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
    }
}
