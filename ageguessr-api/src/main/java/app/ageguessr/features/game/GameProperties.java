package app.ageguessr.features.game;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Rules of the daily game. See plans/done/daily-draw.md for the decisions behind the defaults. */
@Validated
@ConfigurationProperties("app.game.daily")
public record GameProperties(
        // 3 for now; the user may change it after playtests (2026-11-02).
        @Positive int rounds,
        // A photo is not played again before this many years: 3 years of 3 rounds use
        // 3 285 of the ~5 000 approved photos.
        @Positive int replayYears,
        // Draws the game at midnight UTC and at startup. Off by default until the game can
        // be played (a day drawn with no player wastes 3 photos for 3 years), and always
        // off in tests and in the one-off commands (ingestion, hint import).
        boolean drawEnabled) {}
