package app.ageguessr.features.game;

import java.io.Serializable;
import java.util.UUID;

/** Primary key of {@link FreeSeriesRound}: a series and a position in it. */
public record FreeSeriesRoundId(UUID seriesId, short position) implements Serializable {}
