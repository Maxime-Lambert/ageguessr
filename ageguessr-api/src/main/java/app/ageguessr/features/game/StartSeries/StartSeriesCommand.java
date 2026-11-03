package app.ageguessr.features.game.StartSeries;

import jakarta.validation.constraints.NotNull;

/** @param size 5, 10 or 20 ({@link app.ageguessr.features.game.FreeSeries#SIZES}) */
public record StartSeriesCommand(@NotNull Integer size) {}
