package app.ageguessr.features.auth.Login;

import app.ageguessr.features.auth.TokenPair;

/**
 * Signed in (tokens), or the password is right and the two-factor code is still to come:
 * then a challenge, which POST /api/auth/login/totp exchanges with the code for tokens.
 */
public record LoginOutcome(TokenPair tokens, String twoFactorChallenge) {

    static LoginOutcome signedIn(TokenPair tokens) {
        return new LoginOutcome(tokens, null);
    }

    static LoginOutcome twoFactor(String challenge) {
        return new LoginOutcome(null, challenge);
    }
}
