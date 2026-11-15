package app.ageguessr.features.auth.SetUpTwoFactor;

/**
 * What the app needs, once: the secret (to type) and the otpauth address (for the QR code).
 * Never sent again: the server only keeps it encrypted.
 */
public record SetUpTwoFactorResponse(String secret, String otpauthUri) {}
