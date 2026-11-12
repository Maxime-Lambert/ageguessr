package app.ageguessr.features.game.RevealHint;

import app.ageguessr.features.hints.HintCategory;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param categories the categories chosen: as many as the round asks for ({@code hintsToPick}),
 *     each once, checked by the handler since the number depends on the round
 * @param category the single category a page loaded before several hints sends (release of
 *     feature/better-hints-gameplay): one hint, as then, so that an open tab is not stuck.
 *     To remove once no such page can be left open
 */
public record RevealHintCommand(@Size(max = 6) List<@NotNull HintCategory> categories, HintCategory category) {

    /** Sent by a page of before several hints. */
    public boolean legacy() {
        return (categories == null || categories.isEmpty()) && category != null;
    }

    public List<HintCategory> chosen() {
        if (legacy()) {
            return List.of(category);
        }
        return categories == null ? List.of() : categories;
    }

    @AssertTrue(message = "Pick at least one category")
    public boolean isAnyChosen() {
        return !chosen().isEmpty();
    }
}
