package app.ageguessr.features.game.SubmitSecondGuess;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SubmitSecondGuessCommand(@NotNull @Min(18) @Max(100) Integer guess) {}
