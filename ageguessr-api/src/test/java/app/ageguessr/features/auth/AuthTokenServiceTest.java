package app.ageguessr.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import app.ageguessr.shared.security.OpaqueTokenGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AuthTokenServiceTest {

    private static final SecretKey KEY =
            new SecretKeySpec(Base64.getDecoder().decode("S2wyEBMLjFQPUYj29TUDvZRxH/RY1dCCSRpgKJ21dg4="), "HmacSHA256");

    // Issued "now" so that the decoder's expiry check accepts the token.
    private final AuthTokenService service = new AuthTokenService(
            NimbusJwtEncoder.withSecretKey(KEY).algorithm(MacAlgorithm.HS256).build(),
            mock(RefreshTokenRepository.class),
            mock(OpaqueTokenGenerator.class),
            Clock.fixed(Instant.now(), ZoneOffset.UTC),
            "ageguessr-api",
            60,
            90);

    private final JwtDecoder decoder =
            NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();

    @Test
    void anAdminTokenCarriesTheAdminRole() {
        Jwt jwt = decoder.decode(service.issueAccessToken(user(UserRole.ADMIN)));

        assertThat(jwt.getClaimAsStringList(AuthTokenService.ROLES_CLAIM)).containsExactly("ADMIN");
    }

    @Test
    void aRegularTokenCarriesNoRoleClaimAtAll() {
        Jwt jwt = decoder.decode(service.issueAccessToken(user(UserRole.USER)));

        assertThat(jwt.hasClaim(AuthTokenService.ROLES_CLAIM)).isFalse();
        assertThat(jwt.getClaimAsString("email")).isEqualTo("someone@x.com");
    }

    private static User user(UserRole role) {
        return User.builder().id(UUID.randomUUID()).email("someone@x.com").role(role).build();
    }
}
