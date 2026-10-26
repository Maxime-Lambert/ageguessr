package app.ageguessr.features.people;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CurationProperties.class)
class CurationConfig {}
