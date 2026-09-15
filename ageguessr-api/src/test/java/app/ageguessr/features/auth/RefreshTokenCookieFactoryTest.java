package app.ageguessr.features.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class RefreshTokenCookieFactoryTest {

    private final RefreshTokenCookieFactory factory =
            new RefreshTokenCookieFactory("ageguessr_refresh_token", 90, true);

    @Test
    void createBuildsACookieWithTheExpectedAttributes() {
        ResponseCookie cookie = factory.create("raw-token-value");

        assertThat(cookie.getName()).isEqualTo("ageguessr_refresh_token");
        assertThat(cookie.getValue()).isEqualTo("raw-token-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/auth");
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(90));
    }

    @Test
    void clearBuildsAnImmediatelyExpiringEmptyCookie() {
        ResponseCookie cookie = factory.clear();

        assertThat(cookie.getName()).isEqualTo("ageguessr_refresh_token");
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }
}
