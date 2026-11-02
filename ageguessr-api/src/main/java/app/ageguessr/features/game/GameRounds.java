package app.ageguessr.features.game;

import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.PersonRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Reads the rounds of a day with their photo and person, from the people feature. */
@Component
@RequiredArgsConstructor
public class GameRounds {

    private final DailyRoundRepository rounds;
    private final PersonPhotoRepository photos;
    private final PersonRepository people;
    private final Clock clock;

    public List<GameRound> of(LocalDate gameDate) {
        return rounds.findStatesOf(gameDate).stream()
                .map(state -> load(gameDate, state))
                .toList();
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
                .map(state -> load(gameDate, state))
                .orElseThrow(() -> new NotFoundException("No such round today"));
        if (!round.playable()) {
            throw GameErrors.roundNeutralized();
        }
        return round;
    }

    /** A photo deleted between the two reads (person excluded meanwhile) neutralizes the round too. */
    private GameRound load(LocalDate gameDate, DailyRoundRepository.RoundState state) {
        GameRound neutralized = new GameRound(gameDate, state.position(), DailyRoundStatus.NEUTRALIZED, null, null);
        if (state.status() != DailyRoundStatus.PLAYABLE) {
            return neutralized;
        }
        return photos.findById(state.photoId())
                .flatMap(photo -> people.findById(photo.getPersonId())
                        .map(person -> new GameRound(gameDate, state.position(), state.status(), photo, person)))
                .orElse(neutralized);
    }
}
