package app.ageguessr.features.auth;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based one-time passwords (RFC 6238), as every authenticator app computes them:
 * HMAC-SHA1 of the number of 30-second steps since 1970, truncated to 6 digits (RFC 4226).
 * A few lines rather than a library: the algorithm is small, fixed, and tested against
 * the RFC's own vectors (TotpTest).
 */
public final class Totp {

    static final int DIGITS = 6;
    static final long STEP_SECONDS = 30;
    /** One step either side: a phone a little early or late, a code typed as it changes. */
    static final int WINDOW = 1;
    private static final int SECRET_BYTES = 20;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    /** A new secret, base32 as the apps take it (160 bits, the size RFC 4226 recommends). */
    public static String newSecret() {
        byte[] secret = new byte[SECRET_BYTES];
        RANDOM.nextBytes(secret);
        return base32(secret);
    }

    public static long stepAt(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), STEP_SECONDS);
    }

    /**
     * The step a code matches around now, if any later than the last one accepted: a code
     * already used is refused (replay), even within its 90 seconds.
     */
    public static OptionalLong verify(String base32Secret, String code, Instant now, Long lastStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        byte[] key = unbase32(base32Secret);
        long current = stepAt(now);
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if (lastStep != null && step <= lastStep) {
                continue;
            }
            // Constant time: the comparison tells nothing of how many digits matched.
            if (MessageDigest.isEqual(
                    code(key, step).getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }

    /** The address the apps read from the QR code: issuer and account shown in the app. */
    public static String otpauthUri(String account, String base32Secret) {
        String issuer = "AgeGuessr";
        String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + issuer + "&digits=" + DIGITS
                + "&period=" + STEP_SECONDS;
    }

    /** The code of a step: what the app shows. Public for the tests that play the app. */
    public static String codeAt(String base32Secret, long step) {
        return code(unbase32(base32Secret), step);
    }

    static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(step).array());
            // Dynamic truncation (RFC 4226, 5.3): 4 bytes from an offset the hash picks.
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % (int) Math.pow(10, DIGITS));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is part of every JDK", e);
        }
    }

    static String base32(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] unbase32(String text) {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Not base32");
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        return out.array();
    }
}
