package app.ageguessr.features.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy(Set.of("qwerty123456"));

    @ParameterizedTest
    @ValueSource(strings = {"qwerty123456", "QWERTY123456", "Qwerty123456"})
    void refusesAListedPasswordWhateverItsCase(String password) {
        assertRefused(password, "Player", "player@x.com", "PASSWORD_TOO_COMMON");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ageguessr2026", "my-AgeGuessr-pass"})
    void refusesTheSiteName(String password) {
        assertRefused(password, "Player", "player@x.com", "PASSWORD_TOO_COMMON");
    }

    @Test
    void refusesAPasswordContainingTheUsernameWhateverItsCase() {
        assertRefused("i-am-MAXIMUS-42", "Maximus", "someone@x.com", "PASSWORD_CONTAINS_PERSONAL_INFO");
    }

    @Test
    void refusesAPasswordContainingTheEmailName() {
        assertRefused("jean.dupont-1984", "Player", "Jean.Dupont@x.com", "PASSWORD_CONTAINS_PERSONAL_INFO");
    }

    @Test
    void matchesWhateverTheUnicodeNormalizationForm() {
        String decomposed = "rene\u0301e"; // "renée" typed as "e" + combining acute accent
        assertRefused("i-am-ren\u00e9e-1990", decomposed, "someone@x.com", "PASSWORD_CONTAINS_PERSONAL_INFO");
        assertRefused("i-am-" + decomposed + "-1990", "ren\u00e9e", "someone@x.com", "PASSWORD_CONTAINS_PERSONAL_INFO");
    }

    /** Below 4 characters, a username or email name would forbid too many passwords. */
    @Test
    void ignoresShortUsernamesAndEmailNames() {
        assertThatCode(() -> policy.check("max-tortoise-violin", "max", "max@x.com")).doesNotThrowAnyException();
    }

    @Test
    void appliesTheLengthThresholdFromFourCharacters() {
        assertRefused("maxi-tortoise-violin", "maxi", "someone@x.com", "PASSWORD_CONTAINS_PERSONAL_INFO");
    }

    @Test
    void acceptsAnUnrelatedPassword() {
        assertThatCode(() -> policy.check("tortoise-violin-42", "Player", "player@x.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void loadsTheBundledListAtStartup() {
        PasswordPolicy bundled = new PasswordPolicy();

        assertThatThrownBy(() -> bundled.check("Password1234", "Player", "player@x.com"))
                .extracting("errorCode")
                .isEqualTo("PASSWORD_TOO_COMMON");
        assertThatCode(() -> bundled.check("tortoise-violin-42", "Player", "player@x.com"))
                .doesNotThrowAnyException();
    }

    private void assertRefused(String password, String username, String email, String errorCode) {
        assertThatThrownBy(() -> policy.check(password, username, email))
                .extracting("errorCode")
                .isEqualTo(errorCode);
    }
}
