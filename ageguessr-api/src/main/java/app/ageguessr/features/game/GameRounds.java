package app.ageguessr.features.game;

import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads the rounds of a day, or of a free series, with their photo and person, from the
 * people feature.
 */
@Component
@RequiredArgsConstructor
public class GameRounds {

    private final DailyRoundRepository rounds;
    private final FreeSeriesRoundRepository seriesRounds;
    private final PersonPhotoRepository photos;
    private final PersonRepository people;
    private final Clock clock;

    public List<GameRound> of(LocalDate gameDate) {
        return rounds.findStatesOf(gameDate).stream()
                .map(state -> load(gameDate, null, state))
                .toList();
    }

    public List<GameRound> of(FreeSeries series) {
        return seriesRounds.findStatesOf(series.getId()).stream()
                .map(state -> load(series.getCreatedOn(), series.getId(), state))
                .toList();
    }

    /** A round of a series a guess can be made on: a series never closes, until purged. */
    public GameRound playable(FreeSeries series, short position) {
        GameRound round = of(series, position);
        if (!round.playable()) {
            throw GameErrors.roundNeutralized();
        }
        return round;
    }

    /**
     * A round a guess can be made on: today's, and still playable. The date is checked
     * first, so that a round of another day says nothing about its status.
     */
    public GameRound playable(LocalDate gameDate, short position) {
        if (!gameDate.equals(LocalDate.now(clock))) {
            throw GameErrors.gameClosed();
        }
        GameRound round = rounds.findStatesOf(gameDate).stream()
                .filter(state -> state.position() == position)
                .findFirst()
                .map(state -> load(gameDate, null, state))
                .orElseThrow(() -> new NotFoundException("No such round today"));
        if (!round.playable()) {
            throw GameErrors.roundNeutralized();
        }
        return round;
    }

    /**
     * A round of any day, as played: what a report looks at once the round is over, which
     * may be after midnight.
     */
    public GameRound of(LocalDate gameDate, short position) {
        return of(gameDate).stream()
                .filter(round -> round.position() == position)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No such round on this day"));
    }

    /** A round of a series, playable or not. */
    public GameRound of(FreeSeries series, short position) {
        return of(series).stream()
                .filter(round -> round.position() == position)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No such round in this series"));
    }

    /** A photo deleted between the two reads (person excluded meanwhile) neutralizes the round too. */
    private GameRound load(LocalDate gameDate, UUID seriesId, RoundState state) {
        GameRound neutralized =
                new GameRound(gameDate, state.position(), DailyRoundStatus.NEUTRALIZED, null, null, seriesId);
        if (state.status() != DailyRoundStatus.PLAYABLE) {
            return neutralized;
        }
        return photos.findById(state.photoId())
                .flatMap(photo -> people.findById(photo.getPersonId())
                        .map(person ->
                                new GameRound(gameDate, state.position(), state.status(), photo, person, seriesId)))
                .orElse(neutralized);
    }
}
