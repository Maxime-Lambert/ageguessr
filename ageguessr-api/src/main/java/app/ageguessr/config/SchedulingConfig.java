package app.ageguessr.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} methods (the daily draw at midnight UTC). */
@Configuration
@EnableScheduling
class SchedulingConfig {}
