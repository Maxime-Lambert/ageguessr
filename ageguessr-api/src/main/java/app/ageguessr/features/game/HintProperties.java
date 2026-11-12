package app.ageguessr.features.game;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** The hints of a round, in the daily game and in the series alike. */
@Validated
@ConfigurationProperties("app.game.hints")
public record HintProperties(
        // How many categories a player picks after the first guess (user decision,
        // 2026-11-12: 3 of the 6 to start with, showing them all was considered). Fewer
        // when fewer are offered. At most the 7 categories (Literature since V23).
        @Min(1) @Max(7) int perRound) {}
