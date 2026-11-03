package app.ageguessr.features.game;

import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * The code of a challenge: 8 characters of base32 Crockford, no I, L, O or U, so that it
 * reads and types without confusion. 40 bits from SecureRandom: a code cannot be guessed
 * to reach someone else's challenge, and with the rate limit on joining, cannot be found
 * by trying.
 */
@Component
public class ShareCodes {

    static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** A code as typed or pasted: case and surrounding spaces do not matter. */
    public static String normalize(String code) {
        return code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
    }
}
