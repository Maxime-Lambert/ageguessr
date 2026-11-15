package app.ageguessr.features.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

    /** The SHA-1 key of RFC 6238, appendix B. */
    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private static final String RFC_SECRET = Totp.base32(RFC_KEY);

    /** The RFC's 8-digit values, of which an app shows the last 6. */
    @ParameterizedTest
    @CsvSource({"59, 287082", "1111111109, 081804", "1111111111, 050471", "1234567890, 005924", "2000000000, 279037"})
    void computesTheCodesOfTheRfc(long epochSecond, String code) {
        assertThat(Totp.code(RFC_KEY, Totp.stepAt(Instant.ofEpochSecond(epochSecond)))).isEqualTo(code);
    }

    @Test
    void readsBackItsOwnBase32() {
        assertThat(RFC_SECRET).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.unbase32(RFC_SECRET)).isEqualTo(RFC_KEY);
        assertThat(Totp.unbase32("gezd gnbv gy3t qojq gezd gnbv gy3t qojq")).isEqualTo(RFC_KEY);
        String secret = Totp.newSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32(Totp.unbase32(secret))).isEqualTo(secret);
    }

    @Test
    void acceptsTheCodeOfTheStepBeforeAndAfterButNotFurther() {
        Instant now = Instant.ofEpochSecond(1111111111);
        long step = Totp.stepAt(now);

        assertThat(Totp.verify(RFC_SECRET, "050471", now, null)).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_KEY, step - 1), now, null)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_KEY, step + 1), now, null)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_KEY, step - 2), now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_KEY, step + 2), now, null)).isEmpty();
    }

    @Test
    void refusesACodeAlreadyUsed() {
        Instant now = Instant.ofEpochSecond(1111111111);
        long step = Totp.stepAt(now);

        assertThat(Totp.verify(RFC_SECRET, "050471", now, step)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_KEY, step + 1), now, step)).hasValue(step + 1);
    }

    @Test
    void refusesWhatIsNotSixDigits() {
        Instant now = Instant.ofEpochSecond(1111111111);
        assertThat(Totp.verify(RFC_SECRET, null, now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "50471", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, "05047a", now, null)).isEmpty();
        assertThat(Totp.verify(RFC_SECRET, " 050471", now, null)).isEmpty();
    }

    @Test
    void namesTheSiteAndTheAccountForTheApps() {
        assertThat(Totp.otpauthUri("Jane D", "ABC"))
                .isEqualTo("otpauth://totp/AgeGuessr%3AJane%20D?secret=ABC&issuer=AgeGuessr&digits=6&period=30");
    }
}
