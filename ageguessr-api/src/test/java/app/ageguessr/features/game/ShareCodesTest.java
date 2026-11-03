package app.ageguessr.features.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShareCodesTest {

    private final ShareCodes codes = new ShareCodes();

    @Test
    void drawsEightCharactersOfBase32CrockfordMatchingV15() {
        Set<String> drawn = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = codes.next();
            assertThat(code).matches("^[0-9A-HJKMNP-TV-Z]{8}$");
            drawn.add(code);
        }
        assertThat(drawn).hasSize(1000);
    }

    @Test
    void readsACodeAsTypedOrPasted() {
        assertThat(ShareCodes.normalize("  k7f2qx9m ")).isEqualTo("K7F2QX9M");
        assertThat(ShareCodes.normalize(null)).isEmpty();
    }
}
