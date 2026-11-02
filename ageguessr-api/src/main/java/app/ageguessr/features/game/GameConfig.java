package app.ageguessr.features.game;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GameProperties.class)
class GameConfig {

    /**
     * SecureRandom rather than a seeded generator: nobody should be able to predict the
     * games to come from the ones already played. It is also thread-safe, and the draw
     * runs once a day, so its cost does not matter.
     */
    @Bean
    RandomGenerator drawRandom() {
        return new SecureRandom();
    }
}
