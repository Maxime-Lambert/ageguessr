package app.ageguessr.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString("x".repeat(32).getBytes());

    @Test
    void readsBackWhatItEncryptedNeverTheSameTwice() {
        SecretCipher cipher = new SecretCipher(KEY);

        String once = cipher.encrypt("JBSWY3DPEHPK3PXP");
        String twice = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(once).isNotEqualTo(twice).doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(once)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(twice)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void refusesAnotherKeyOrATamperedValue() {
        String stored = new SecretCipher(KEY).encrypt("JBSWY3DPEHPK3PXP");
        byte[] bytes = Base64.getDecoder().decode(stored);
        bytes[bytes.length - 1] ^= 1;

        assertThatThrownBy(() -> new SecretCipher(OTHER_KEY).decrypt(stored)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretCipher(KEY).decrypt(Base64.getEncoder().encodeToString(bytes)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesAKeyOfTheWrongSizeAtStartup() {
        assertThatThrownBy(() -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
