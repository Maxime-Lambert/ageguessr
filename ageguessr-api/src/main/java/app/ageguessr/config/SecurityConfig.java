package app.ageguessr.config;

import app.ageguessr.shared.ratelimit.RateLimitFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * CSRF is disabled: every protected endpoint is authorized via a Bearer header, which
 * a cross-site page cannot attach without XSS (CSRF specifically targets
 * cookie-authenticated mutations). The one cookie-driven flow (refresh/logout) is
 * additionally protected by the cookie's own SameSite=Lax (see
 * RefreshTokenCookieFactory), which blocks the cookie from being sent on cross-site
 * requests for anything but a top-level GET navigation - both endpoints are POST, so
 * that path is closed too. No CSRF token needed on top of that.
 */
@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_AUTH_PATHS = {
        "/api/health",
        "/api/auth/register",
        "/api/auth/login",
        "/api/auth/refresh",
        "/api/auth/confirm-email",
        "/api/auth/resend-verification",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/api/auth/confirm-account-deletion"
    };

    private static final String[] PUBLIC_GAME_GET_PATHS = {"/api/images/*"};

    private final RateLimitFilter rateLimitFilter;

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_AUTH_PATHS)
                        .permitAll()
                        // Game endpoints are guest-playable (docs/decisions/architecture.md):
                        // each one MUST be added here, or guest play silently breaks
                        // behind a 401. GET only: nothing else exists on these paths.
                        .requestMatchers(HttpMethod.GET, PUBLIC_GAME_GET_PATHS)
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
