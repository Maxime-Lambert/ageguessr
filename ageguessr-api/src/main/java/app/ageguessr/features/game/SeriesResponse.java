package app.ageguessr.features.game;

import java.util.List;
import java.util.UUID;

/**
 * A free series as one player sees it.
 *
 * @param size the length asked for; {@code rounds} may hold fewer when the pool ran short
 * @param shareCode the code of the challenge once shared, else null
 * @param creator whether this player created the series (only the creator shares it)
 */
public record SeriesResponse(
        UUID id, int size, List<RoundView> rounds, GameScore score, String shareCode, boolean creator) {}
