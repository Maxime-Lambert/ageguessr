package app.ageguessr.config;

import app.ageguessr.shared.security.JwtClaims;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Issues and validates access tokens with a single shared HS256 secret — no separate
 * JWT library needed, spring-security-oauth2-jose (already pulled in by the
 * oauth2-resource-server starter) provides both Nimbus-backed encoder and decoder.
 * Defining these beans ourselves makes Spring Boot's own
 * spring.security.oauth2.resourceserver.jwt.* autoconfiguration back off
 * (@ConditionalOnMissingBean) — those properties must never also be set.
 */
@Configuration
public class JwtConfig {

    @Bean
    SecretKey jwtSecretKey(@Value("${app.jwt.secret}") String base64Secret) {
        return new SecretKeySpec(Base64.getDecoder().decode(base64Secret), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey).algorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Turns the "roles" claim (["ADMIN"]) into ROLE_ADMIN authorities, which
     * SecurityConfig checks on /api/admin/**. Picked up by the resource server as the
     * only JwtAuthenticationConverter bean. Without it, Spring would read the standard
     * "scope" claim, which these tokens do not carry.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(JwtClaims.ROLES);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(roles);
        return converter;
    }
}
