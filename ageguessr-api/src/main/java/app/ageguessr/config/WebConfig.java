package app.ageguessr.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Resolves request.getRemoteAddr() from X-Forwarded-For (leftmost value), so IP-based
 * rate limiting sees the real client instead of the edge Caddy container. Safe to trust
 * blindly only because the backend is reachable solely through the Caddy chain, which
 * overwrites the header with a single, unspoofable value: the VPS gateway derives it
 * from Cloudflare's CF-Connecting-IP (trusted only from Cloudflare's IP ranges) and the
 * edge forwards it as-is (see Caddyfile and docs/decisions/architecture.md).
 */
@Configuration
public class WebConfig {

    @Bean
    FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
        FilterRegistrationBean<ForwardedHeaderFilter> bean =
                new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
