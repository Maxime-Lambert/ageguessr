package app.ageguessr.features.game.StartSeries;

import app.ageguessr.features.game.FreeSeries;
import app.ageguessr.features.game.FreeSeriesPlayer;
import app.ageguessr.features.game.FreeSeriesPlayerRepository;
import app.ageguessr.features.game.FreeSeriesRepository;
import app.ageguessr.features.game.FreeSeriesRound;
import app.ageguessr.features.game.FreeSeriesRoundRepository;
import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.SeriesResponse;
import app.ageguessr.features.game.SeriesViews;
import app.ageguessr.shared.exceptions.ValidationException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Draws a new series for a player, who becomes its first player. */
@Service
@RequiredArgsConstructor
public class StartSeriesHandler {

    private final SeriesCandidateRepository candidates;
    private final SeriesDraw draw;
    private final FreeSeriesRepository series;
    private final FreeSeriesRoundRepository rounds;
    private final FreeSeriesPlayerRepository players;
    private final SeriesViews views;
    private final Clock clock;

    @Transactional
    public SeriesResponse handle(PlayerKey player, StartSeriesCommand command) {
        if (!FreeSeries.SIZES.contains(command.size())) {
            throw new ValidationException("INVALID_SIZE", "A series has 5, 10 or 20 photos");
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);
        List<UUID> photos = draw.draw(
                candidates.findCandidates(), candidates.findDailyPhotos(today), candidates.findSeenBy(player),
                command.size());
        if (photos.isEmpty()) {
            throw GameErrors.noPhotos();
        }
        FreeSeries created = series.save(FreeSeries.create(player, command.size(), now, today));
        List<FreeSeriesRound> drawn = new ArrayList<>();
        for (int i = 0; i < photos.size(); i++) {
            drawn.add(FreeSeriesRound.builder()
                    .seriesId(created.getId())
                    .position((short) (i + 1))
                    .photoId(photos.get(i))
                    .build());
        }
        rounds.saveAll(drawn);
        players.save(FreeSeriesPlayer.of(created.getId(), player, null, now));
        rounds.flush();
        return views.of(created, player);
    }
}
