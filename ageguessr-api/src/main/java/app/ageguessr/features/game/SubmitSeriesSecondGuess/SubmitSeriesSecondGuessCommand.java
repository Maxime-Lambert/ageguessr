package app.ageguessr.features.game.SubmitSeriesSecondGuess;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubmitSeriesSecondGuessCommand(@NotNull @Min(18) @Max(100) Integer guess) {}
