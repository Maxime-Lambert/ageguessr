package app.ageguessr.features.game.RevealSeriesHint;

import app.ageguessr.features.hints.HintCategory;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * @param categories the categories chosen: as many as the round asks for ({@code hintsToPick}),
 *     each once, checked by the handler since the number depends on the round
 */
public record RevealSeriesHintCommand(@NotEmpty @Size(max = 6) List<@NotNull HintCategory> categories) {}
