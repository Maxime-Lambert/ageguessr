package app.ageguessr.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Registered now even though Caddy/Cloudflare aren't deployed yet: harmless locally
 * (no X-Forwarded-For header is ever sent), and without it every request in prod would
 * resolve to Caddy's own IP once the edge is live — silently breaking IP-based rate
 * limiting for every real user at once.
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
