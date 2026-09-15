package app.ageguessr.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Generates opaque, high-entropy tokens (refresh tokens, email verification tokens)
 * and hashes them with SHA-256 for storage — the raw value is never persisted, only
 * ever handed to the caller once (to put in a cookie or an email link) and re-derived
 * from a presented value at lookup time via {@link #hash(String)}.
 */
@Component
public class OpaqueTokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    public GeneratedToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedToken(rawValue, hash(rawValue));
    }

    public String hash(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed available on every JDK platform (JLS/JCA requirement).
            throw new IllegalStateException(e);
        }
    }

    public record GeneratedToken(String rawValue, String hash) {}
}
