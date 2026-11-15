package app.ageguessr.features.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AccountRetentionProperties.class)
class AuthConfig {}
