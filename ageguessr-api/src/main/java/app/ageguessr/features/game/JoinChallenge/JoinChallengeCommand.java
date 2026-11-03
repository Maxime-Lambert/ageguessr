package app.ageguessr.features.game.JoinChallenge;

import jakarta.validation.constraints.Pattern;

/**
 * @param nickname required for a guest, the format of a username (3-20 letters, digits,
 *     "_" or "-"); ignored for an account, which shows its username
 */
public record JoinChallengeCommand(@Pattern(regexp = "^[A-Za-z0-9_-]{3,20}$") String nickname) {}
