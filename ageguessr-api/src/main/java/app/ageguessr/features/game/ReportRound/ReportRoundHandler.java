package app.ageguessr.features.game.ReportRound;

import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.FreeSeries;
import app.ageguessr.features.game.FreeSeriesPlayRepository;
import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GameRound;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.PlayedRound;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.RoundStep;
import app.ageguessr.features.game.SeriesViews;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintReport;
import app.ageguessr.features.hints.HintReportReason;
import app.ageguessr.features.hints.HintReportRepository;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import app.ageguessr.features.people.PhotoReport;
import app.ageguessr.features.people.PhotoReportReason;
import app.ageguessr.features.people.PhotoReportRepository;
import app.ageguessr.shared.reports.ReportStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A player reports the photo or the hint of a round they finished. The report itself is
 * anonymous (PhotoReport, HintReport); what keeps a player from reporting the same round
 * twice is the mark on their played round, so a second report changes nothing. Nothing is
 * ever taken out of the game here: the admin decides (DailyRoundStatus).
 */
@Service
@RequiredArgsConstructor
public class ReportRoundHandler {

    private final GameRounds rounds;
    private final SeriesViews series;
    private final GamePlayRepository dailyPlays;
    private final FreeSeriesPlayRepository seriesPlays;
    private final DailyRoundRepository dailyRounds;
    private final PhotoReportRepository photoReports;
    private final HintReportRepository hintReports;
    private final HintRepository hints;
    private final ReportAlerts alerts;
    private final Clock clock;

    @Transactional
    public void handleDaily(PlayerKey player, LocalDate gameDate, short position, ReportRoundCommand command) {
        Reason reason = Reason.of(command);
        GameRound round = rounds.of(gameDate, position);
        var play = dailyPlays.findOf(player, gameDate, position).orElseThrow(GameErrors::roundNotFinished);
        boolean today = gameDate.equals(LocalDate.now(clock));
        report(round, play, reason, today, () -> dailyPlays.saveAndFlush(play));
    }

    @Transactional
    public void handleSeries(PlayerKey player, UUID seriesId, short position, ReportRoundCommand command) {
        Reason reason = Reason.of(command);
        FreeSeries found = series.ofPlayer(seriesId, player);
        GameRound round = rounds.of(found, position);
        var play = seriesPlays.findOf(player, seriesId, position).orElseThrow(GameErrors::roundNotFinished);
        report(round, play, reason, false, () -> seriesPlays.saveAndFlush(play));
    }

    /** @param fromTodaysGame the round is one of today's game */
    private void report(GameRound round, PlayedRound play, Reason reason, boolean fromTodaysGame, Runnable save) {
        if (play.step() != RoundStep.FINISHED) {
            throw GameErrors.roundNotFinished();
        }
        if (reason.photo() != null) {
            // A photo is urgent when it is in today's game, wherever it was seen: checked there.
            reportPhoto(round, play, reason.photo(), save);
        } else {
            reportHint(play, reason.hint(), fromTodaysGame, save);
        }
    }

    private void reportPhoto(GameRound round, PlayedRound play, PhotoReportReason reason, Runnable save) {
        if (!round.playable()) {
            throw GameErrors.roundNeutralized();
        }
        if (!play.progress().reportPhoto()) {
            return;
        }
        saveMark(save);
        UUID photoId = round.photo().getId();
        photoReports.save(PhotoReport.open(photoId, reason, clock.instant()));
        long open = photoReports.countByPhotoIdAndStatus(photoId, ReportStatus.OPEN);
        long openForReason = photoReports.countByPhotoIdAndReasonAndStatus(photoId, reason, ReportStatus.OPEN);
        boolean inTodaysGame = dailyRounds.existsByGameDateAndPhotoId(LocalDate.now(clock), photoId);
        if (alerts.photoDue(open, openForReason, reason, inTodaysGame)) {
            alerts.photo(round.person(), reason, open, inTodaysGame);
        }
    }

    /** Only the hint the player was shown, and while it is still in the game. */
    private void reportHint(PlayedRound play, HintReportReason reason, boolean fromTodaysGame, Runnable save) {
        Hint hint = (play.getHintId() == null ? null : hints.findById(play.getHintId())
                .filter(found -> found.getReviewStatus() == HintReviewStatus.APPROVED)
                .orElse(null));
        if (hint == null) {
            throw GameErrors.nothingToReport();
        }
        if (!play.progress().reportHint()) {
            return;
        }
        saveMark(save);
        hintReports.save(HintReport.open(hint.getId(), reason, clock.instant()));
        long open = hintReports.countByHintIdAndStatus(hint.getId(), ReportStatus.OPEN);
        if (alerts.hintDue(open, fromTodaysGame)) {
            alerts.hint(hint, reason.name(), open, fromTodaysGame);
        }
    }

    /** Two tabs reporting the same round at once: the second one fails instead of counting twice. */
    private static void saveMark(Runnable save) {
        try {
            save.run();
        } catch (ObjectOptimisticLockingFailureException e) {
            throw GameErrors.wrongStep();
        }
    }

    /** The reason, read from the list of its target: exactly one of the two is set. */
    record Reason(PhotoReportReason photo, HintReportReason hint) {

        static Reason of(ReportRoundCommand command) {
            try {
                return switch (command.target()) {
                    case PHOTO -> new Reason(PhotoReportReason.valueOf(command.reason()), null);
                    case HINT -> new Reason(null, HintReportReason.valueOf(command.reason()));
                };
            } catch (IllegalArgumentException e) {
                throw GameErrors.invalidReason();
            }
        }
    }
}
