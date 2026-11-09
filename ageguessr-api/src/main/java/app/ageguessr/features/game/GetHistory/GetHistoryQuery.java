package app.ageguessr.features.game.GetHistory;

import jakarta.validation.constraints.Min;

/** @param page from 0, the most recent days first; absent means the first page */
public record GetHistoryQuery(@Min(0) Integer page) {

    int pageOrFirst() {
        return page == null ? 0 : page;
    }
}
