package app.ageguessr.features.friends;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(FriendsProperties.class)
class FriendsConfig {}
