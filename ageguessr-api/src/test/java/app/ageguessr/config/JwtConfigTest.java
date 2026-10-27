package app.ageguessr.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

class JwtConfigTest {

    private final JwtAuthenticationConverter converter = new JwtConfig().jwtAuthenticationConverter();

    /**
     * Roles and scopes only. Spring Security 7 also adds a FACTOR_BEARER authority (how
     * the user authenticated, for multi-factor rules); it is not a permission.
     */
    private List<String> permissions(Jwt jwt) {
        return converter.convert(jwt).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_") || a.startsWith("SCOPE_"))
                .toList();
    }

    @Test
    void turnsTheRolesClaimIntoRoleAuthorities() {
        assertThat(permissions(jwt(List.of("ADMIN")))).containsExactly("ROLE_ADMIN");
    }

    @Test
    void aTokenWithoutRolesHasNoAuthority() {
        assertThat(permissions(jwt(null))).isEmpty();
    }

    @Test
    void ignoresTheStandardScopeClaim() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject("u")
                .claim("scope", "admin")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();

        assertThat(permissions(jwt)).isEmpty();
    }

    private static Jwt jwt(List<String> roles) {
        Jwt.Builder builder = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject("u")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (roles != null) {
            builder.claim("roles", roles);
        }
        return builder.build();
    }
}
