package app.ageguessr.features.game.ShareSeries;

import jakarta.validation.constraints.Pattern;

/**
 * @param nickname the name of a guest creator in the challenge, the format of a username
 *     (3-20 letters, digits, "_" or "-"); ignored for an account, which shows its username
 */
public record ShareSeriesCommand(@Pattern(regexp = "^[A-Za-z0-9_-]{3,20}$") String nickname) {}
