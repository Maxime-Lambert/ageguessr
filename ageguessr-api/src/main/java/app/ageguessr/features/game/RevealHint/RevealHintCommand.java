package app.ageguessr.features.game.RevealHint;

import app.ageguessr.features.hints.HintCategory;
import jakarta.validation.constraints.NotNull;

public record RevealHintCommand(@NotNull HintCategory category) {}
