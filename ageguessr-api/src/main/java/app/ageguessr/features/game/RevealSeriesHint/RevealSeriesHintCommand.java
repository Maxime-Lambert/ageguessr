package app.ageguessr.features.game.RevealSeriesHint;

import app.ageguessr.features.hints.HintCategory;
import jakarta.validation.constraints.NotNull;

public record RevealSeriesHintCommand(@NotNull HintCategory category) {}
