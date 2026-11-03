package app.ageguessr.features.game;

import java.util.UUID;

/**
 * A round of a day or of a series, with whether it can still be played: read from its
 * photo at each request (DailyRoundRepository, FreeSeriesRoundRepository).
 */
public record RoundState(short position, UUID photoId, DailyRoundStatus status) {}
