package app.ageguessr.features.people.ListPendingPhotos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** How many pending photos to return: a small batch, the page fetches more as it goes. */
public record ListPendingPhotosQuery(@Min(1) @Max(50) Integer limit) {

    static final int DEFAULT_LIMIT = 20;

    public ListPendingPhotosQuery {
        // An absent ?limit binds to null; the default applies before validation runs.
        if (limit == null) {
            limit = DEFAULT_LIMIT;
        }
    }
}
