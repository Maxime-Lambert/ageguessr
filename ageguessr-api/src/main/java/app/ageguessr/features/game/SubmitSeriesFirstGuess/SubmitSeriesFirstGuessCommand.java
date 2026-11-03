package app.ageguessr.features.game.SubmitSeriesFirstGuess;

import app.ageguessr.shared.i18n.Language;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * @param guess the age guessed, from 18 (no minor on a photo, V8) to 100
 * @param language of the hint and of the name revealed, fixed for the round
 */
public record SubmitSeriesFirstGuessCommand(
        @NotNull @Min(18) @Max(100) Integer guess, @NotNull Language language) {}
