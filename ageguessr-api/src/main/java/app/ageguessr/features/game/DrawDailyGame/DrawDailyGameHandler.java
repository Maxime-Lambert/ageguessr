package app.ageguessr.features.game.DrawDailyGame;

import app.ageguessr.features.game.DailyRound;
import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.GameProperties;
import app.ageguessr.features.people.PersonPhoto;
import app.ageguessr.features.people.PersonPhotoRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Returns the game of a day, drawing it first if nobody has yet. Idempotent and safe to
 * call concurrently (midnight job, startup, several instances): the primary key of V12
 * refuses a second draw of the same day, and the loser reads the winner's rounds.
 *
 * <p>A TransactionTemplate rather than {@code @Transactional}: the conflict surfaces when
 * the transaction is already doomed, and the winner's rounds must be read in a new one,
 * after it rolled back. Catching it inside an annotated method would leave nothing to
 * read them with.
 */
@Slf4j
@Service
public class DrawDailyGameHandler {

    private final DailyRoundRepository rounds;
    private final DrawCandidateRepository candidates;
    private final PersonPhotoRepository photos;
    private final DailyDraw draw;
    private final GameProperties properties;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public DrawDailyGameHandler(
            DailyRoundRepository rounds,
            DrawCandidateRepository candidates,
            PersonPhotoRepository photos,
            DailyDraw draw,
            GameProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.rounds = rounds;
        this.candidates = candidates;
        this.photos = photos;
        this.draw = draw;
        this.properties = properties;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public List<DailyRound> drawIfMissing(LocalDate day) {
        try {
            return transaction.execute(status -> existingOrNew(day));
        } catch (DataIntegrityViolationException e) {
            log.info("Game of {} drawn concurrently, reading the other draw", day);
            return rounds.findByGameDateOrderByPosition(day);
        }
    }

    private List<DailyRound> existingOrNew(LocalDate day) {
        List<DailyRound> existing = rounds.findByGameDateOrderByPosition(day);
        if (!existing.isEmpty()) {
            return existing;
        }
        DailyDraw.Outcome outcome = draw.draw(candidates.findCandidates(), day);
        Instant now = clock.instant();
        List<DailyRound> drawn = new ArrayList<>();
        for (int i = 0; i < outcome.photoIds().size(); i++) {
            drawn.add(DailyRound.builder()
                    .gameDate(day)
                    .position((short) (i + 1))
                    .photoId(outcome.photoIds().get(i))
                    .drawnAt(now)
                    .build());
        }
        // Rounds first: a concurrent draw fails here, before it locks any photo row that
        // the winner is about to update.
        List<DailyRound> saved = rounds.saveAllAndFlush(drawn);
        renewPublicIds(outcome.photoIds());
        report(day, outcome);
        return saved;
    }

    /**
     * A new public id for each photo drawn: the id of a game already played, with its
     * answer, may circulate, and must not point to the photo when it comes back years
     * later (architecture.md, image anti-cheat).
     */
    private void renewPublicIds(List<UUID> photoIds) {
        List<PersonPhoto> drawnPhotos = photos.findAllById(photoIds);
        drawnPhotos.forEach(photo -> photo.setPublicId(UUID.randomUUID()));
        photos.saveAllAndFlush(drawnPhotos);
    }

    /** Ids only: a name or a Commons file name in the logs would give the game away. */
    private void report(LocalDate day, DailyDraw.Outcome outcome) {
        int count = outcome.photoIds().size();
        if (count < properties.rounds()) {
            log.error("Game of {}: only {} of {} rounds, too few approved photos in distinct boxes",
                    day, count, properties.rounds());
        }
        if (outcome.replayedEarly() > 0) {
            log.warn("Game of {}: {} photo(s) played again before {} years, the pool is running out",
                    day, outcome.replayedEarly(), properties.replayYears());
        }
        log.info("Game of {} drawn: {} round(s), photos {}", day, count, outcome.photoIds());
    }
}
