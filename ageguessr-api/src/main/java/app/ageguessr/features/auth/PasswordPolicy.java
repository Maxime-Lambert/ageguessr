package app.ageguessr.features.auth;

import app.ageguessr.shared.exceptions.ValidationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * What a new password must not be, beyond its format (@Password): a password found in
 * data breaches, or one built from the site name, the username or the email. Compared
 * case-insensitively, so "Qwerty123456" is refused like "qwerty123456", and after
 * Unicode NFC normalization, so an "é" typed as "e" + combining accent still matches
 * a precomposed "é". Only for
 * passwords being set - an existing account keeps logging in with the one it has.
 */
@Component
public class PasswordPolicy {

    static final String COMMON_PASSWORDS_RESOURCE = "/security/common-passwords.txt";

    private static final String SITE_NAME = "ageguessr";

    /** Below this, a username or email name ("max") would forbid too many passwords. */
    private static final int MIN_PERSONAL_TERM_LENGTH = 4;

    private final Set<String> commonPasswords;

    public PasswordPolicy() {
        this(loadCommonPasswords());
    }

    PasswordPolicy(Set<String> commonPasswords) {
        this.commonPasswords = commonPasswords;
    }

    public void check(String password, String username, String email) {
        String lowered = normalize(password);
        if (commonPasswords.contains(lowered) || lowered.contains(SITE_NAME)) {
            throw new ValidationException("PASSWORD_TOO_COMMON", "This password is too common");
        }
        if (containsPersonalTerm(lowered, username) || containsPersonalTerm(lowered, emailName(email))) {
            throw new ValidationException(
                    "PASSWORD_CONTAINS_PERSONAL_INFO", "The password must not contain your username or email");
        }
    }

    private static boolean containsPersonalTerm(String loweredPassword, String term) {
        return term != null
                && term.length() >= MIN_PERSONAL_TERM_LENGTH
                && loweredPassword.contains(normalize(term));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    private static String emailName(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at < 0 ? email : email.substring(0, at);
    }

    /** A missing list fails startup rather than silently accepting every password. */
    private static Set<String> loadCommonPasswords() {
        InputStream stream = PasswordPolicy.class.getResourceAsStream(COMMON_PASSWORDS_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing classpath resource " + COMMON_PASSWORDS_RESOURCE);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isEmpty())
                    .map(PasswordPolicy::normalize)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
