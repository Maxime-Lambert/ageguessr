package app.ageguessr.features.game;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Rules of the daily game. See plans/active-plan.md for the decisions behind the defaults. */
@Validated
@ConfigurationProperties("app.game.daily")
public record GameProperties(
        // 3 for now; the user may change it after playtests (2026-11-02).
        @Positive int rounds,
        // A photo is not played again before this many years: 3 years of 3 rounds use
        // 3 285 of the ~5 000 approved photos.
        @Positive int replayYears,
        // Draws the game at midnight UTC and at startup. Off in tests and in the one-off
        // commands (ingestion, hint import), which must not draw anything.
        boolean drawEnabled) {}
