package app.ageguessr.features.auth.Login;

/** The password is right; the front asks for the code and sends it with the challenge. */
public record TwoFactorChallengeResponse(boolean twoFactorRequired, String challenge) {}
