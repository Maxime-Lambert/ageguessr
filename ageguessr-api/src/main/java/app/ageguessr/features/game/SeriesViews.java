package app.ageguessr.features.game;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** What a player sees of a series, and who may see it. */
@Component
@RequiredArgsConstructor
public class SeriesViews {

    private final FreeSeriesRepository series;
    private final FreeSeriesPlayerRepository players;
    private final FreeSeriesPlayRepository plays;
    private final GameRounds rounds;
    private final RoundViews views;

    /**
     * A series one of its players asks for. Anyone else gets the same 404 as for a series
     * that does not exist: its id says nothing, and its photos stay with its players.
     */
    public FreeSeries ofPlayer(UUID seriesId, PlayerKey player) {
        return series.findById(seriesId)
                .filter(found -> players.findOf(found.getId(), player).isPresent())
                .orElseThrow(GameErrors::seriesNotFound);
    }

    public SeriesResponse of(FreeSeries found, PlayerKey player) {
        Map<Short, FreeSeriesPlay> played = plays.findOf(player, found.getId()).stream()
                .collect(Collectors.toMap(FreeSeriesPlay::getPosition, Function.identity()));
        List<RoundView> roundViews = rounds.of(found).stream()
                .map(round -> views.of(round, played.get(round.position())))
                .toList();
        return new SeriesResponse(
                found.getId(),
                found.getSize(),
                roundViews,
                GameScore.of(roundViews),
                found.getShareCode(),
                found.isCreatedBy(player));
    }
}
