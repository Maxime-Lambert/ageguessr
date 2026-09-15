package app.ageguessr.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A separate, higher-priority filter chain scoped only to /test-support/**, active
 * only under the "e2e" profile - the main SecurityFilterChain in SecurityConfig is
 * completely unaware of this path and never permits it. This means the carve-out
 * cannot exist at all unless the e2e profile is active, rather than relying on a
 * runtime check that could be misconfigured.
 */
@Configuration
@Profile("e2e")
public class TestSupportSecurityConfig {

    @Bean
    @Order(0)
    SecurityFilterChain testSupportFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/test-support/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .cors(Customizer.withDefaults());
        return http.build();
    }
}
