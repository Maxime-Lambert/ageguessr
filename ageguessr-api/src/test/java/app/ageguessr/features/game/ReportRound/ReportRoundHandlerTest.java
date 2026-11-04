package app.ageguessr.features.game.ReportRound;

import static app.ageguessr.features.game.GameFixtures.AGE;
import static app.ageguessr.features.game.GameFixtures.CLOCK;
import static app.ageguessr.features.game.GameFixtures.NOW;
import static app.ageguessr.features.game.GameFixtures.TODAY;
import static app.ageguessr.features.game.GameFixtures.hints;
import static app.ageguessr.features.game.GameFixtures.neutralizedRound;
import static app.ageguessr.features.game.GameFixtures.playableRound;
import static app.ageguessr.features.game.GameFixtures.started;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.game.DailyRoundRepository;
import app.ageguessr.features.game.FreeSeries;
import app.ageguessr.features.game.FreeSeriesPlay;
import app.ageguessr.features.game.FreeSeriesPlayRepository;
import app.ageguessr.features.game.GamePlay;
import app.ageguessr.features.game.GamePlayRepository;
import app.ageguessr.features.game.GameRound;
import app.ageguessr.features.game.GameRounds;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.SeriesViews;
import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintReport;
import app.ageguessr.features.hints.HintReportReason;
import app.ageguessr.features.hints.HintReportRepository;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import app.ageguessr.features.people.PhotoReport;
import app.ageguessr.features.people.PhotoReportReason;
import app.ageguessr.features.people.PhotoReportRepository;
import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.EmailSender;
import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.i18n.Language;
import app.ageguessr.shared.reports.ReportStatus;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class ReportRoundHandlerTest {

    private static final short POSITION = 1;

    private final GameRounds rounds = mock(GameRounds.class);
    private final SeriesViews series = mock(SeriesViews.class);
    private final GamePlayRepository dailyPlays = mock(GamePlayRepository.class);
    private final FreeSeriesPlayRepository seriesPlays = mock(FreeSeriesPlayRepository.class);
    private final DailyRoundRepository dailyRounds = mock(DailyRoundRepository.class);
    private final PhotoReportRepository photoReports = mock(PhotoReportRepository.class);
    private final HintReportRepository hintReports = mock(HintReportRepository.class);
    private final HintRepository hintRepository = mock(HintRepository.class);
    private final EmailSender emails = mock(EmailSender.class);
    private final ReportRoundHandler handler = new ReportRoundHandler(
            rounds,
            series,
            dailyPlays,
            seriesPlays,
            dailyRounds,
            photoReports,
            hintReports,
            hintRepository,
            new ReportAlerts(emails, "admin@ageguessr.test", "https://ageguessr.app", 3),
            CLOCK);
    private final PlayerKey guest = PlayerKey.guest(UUID.randomUUID());

    private GameRound round;

    @BeforeEach
    void setUp() {
        round = playableRound(POSITION);
        when(rounds.of(TODAY, POSITION)).thenReturn(round);
    }

    private GamePlay finishedPlay() {
        GamePlay play = started(guest, POSITION, AGE);
        when(dailyPlays.findOf(guest, TODAY, POSITION)).thenReturn(Optional.of(play));
        return play;
    }

    private GamePlay finishedPlayWithHint(Hint hint) {
        GamePlay play = started(guest, POSITION, 30);
        play.revealHint(hint.getCategory(), hint.getId());
        play.finishWithSecondGuess(38, AGE, NOW);
        when(dailyPlays.findOf(guest, TODAY, POSITION)).thenReturn(Optional.of(play));
        when(hintRepository.findById(hint.getId())).thenReturn(Optional.of(hint));
        return play;
    }

    private static Hint hint() {
        return hints(HintCategory.MUSIC, 1).getFirst();
    }

    private static ReportRoundCommand photo(PhotoReportReason reason) {
        return new ReportRoundCommand(ReportTarget.PHOTO, reason.name());
    }

    private static ReportRoundCommand hintReport(HintReportReason reason) {
        return new ReportRoundCommand(ReportTarget.HINT, reason.name());
    }

    private void openPhotoReports(long all, long forReason) {
        when(photoReports.countByPhotoIdAndStatus(round.photo().getId(), ReportStatus.OPEN)).thenReturn(all);
        when(photoReports.countByPhotoIdAndReasonAndStatus(eq(round.photo().getId()), any(), eq(ReportStatus.OPEN)))
                .thenReturn(forReason);
    }

    @Test
    void reportsThePhotoOfAFinishedRoundAnonymously() {
        GamePlay play = finishedPlay();
        openPhotoReports(1, 1);

        handler.handleDaily(guest, TODAY, POSITION, photo(PhotoReportReason.WRONG_PERSON));

        assertThat(play.isPhotoReported()).isTrue();
        verify(dailyPlays).saveAndFlush(play);
        ArgumentCaptor<PhotoReport> saved = ArgumentCaptor.forClass(PhotoReport.class);
        verify(photoReports).save(saved.capture());
        assertThat(saved.getValue().getPhotoId()).isEqualTo(round.photo().getId());
        assertThat(saved.getValue().getReason()).isEqualTo(PhotoReportReason.WRONG_PERSON);
        assertThat(saved.getValue().getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void aSecondReportOfTheSameRoundAddsNothing() {
        GamePlay play = finishedPlay();
        play.progress().reportPhoto();

        handler.handleDaily(guest, TODAY, POSITION, photo(PhotoReportReason.OTHER));

        verify(dailyPlays, never()).saveAndFlush(any());
        verify(photoReports, never()).save(any());
    }

    @Test
    void refusesARoundNotFinished() {
        GamePlay play = started(guest, POSITION, 30);
        when(dailyPlays.findOf(guest, TODAY, POSITION)).thenReturn(Optional.of(play));

        assertThatThrownBy(() -> handler.handleDaily(guest, TODAY, POSITION, photo(PhotoReportReason.OTHER)))
                .extracting("errorCode")
                .isEqualTo("ROUND_NOT_FINISHED");
        verify(photoReports, never()).save(any());
    }

    @Test
    void refusesARoundThePlayerNeverStarted() {
        when(dailyPlays.findOf(guest, TODAY, POSITION)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handleDaily(guest, TODAY, POSITION, photo(PhotoReportReason.OTHER)))
                .extracting("errorCode")
                .isEqualTo("ROUND_NOT_FINISHED");
    }

    @Test
    void refusesTheReasonOfTheOtherTarget() {
        finishedPlay();

        assertThatThrownBy(() -> handler.handleDaily(
                        guest, TODAY, POSITION, new ReportRoundCommand(ReportTarget.PHOTO, "WRONG_YEAR")))
                .extracting("errorCode")
                .isEqualTo("INVALID_REASON");
        assertThatThrownBy(() -> handler.handleDaily(
                        guest, TODAY, POSITION, new ReportRoundCommand(ReportTarget.HINT, "LOOKS_MINOR")))
                .extracting("errorCode")
                .isEqualTo("INVALID_REASON");
    }

    @Test
    void aNeutralizedRoundHasNoPhotoToReport() {
        when(rounds.of(TODAY, POSITION)).thenReturn(neutralizedRound(POSITION));
        finishedPlay();

        assertThatThrownBy(() -> handler.handleDaily(guest, TODAY, POSITION, photo(PhotoReportReason.OTHER)))
                .extracting("errorCode")
                .isEqualTo("ROUND_NEUTRALIZED");
        verify(photoReports, never()).save(any());
    }

    @Test
    void twoTabsReportingAtOnceCountOnce() {
        GamePlay play = finishedPlay();
        when(dailyPlays.saveAndFlush(play)).thenThrow(new ObjectOptimisticLockingFailureException(GamePlay.class, "id"));

        assertThatThrownBy(() -> handler.handleDaily(guest, TODAY, POSITION, photo(PhotoReportReason.OTHER)))
                .extracting("errorCode")
                .isEqualTo("WRONG_STEP");
        verify(photoReports, never()).save(any());
    }

    @Test
    void reportsTheHintTheRoundShowed() {
        Hint hint = hint();
        GamePlay play = finishedPlayWithHint(hint);

        handler.handleDaily(guest, TODAY, POSITION, hintReport(HintReportReason.WRONG_YEAR));

        assertThat(play.isHintReported()).isTrue();
        assertThat(play.isPhotoReported()).isFalse();
        ArgumentCaptor<HintReport> saved = ArgumentCaptor.forClass(HintReport.class);
        verify(hintReports).save(saved.capture());
        assertThat(saved.getValue().getHintId()).isEqualTo(hint.getId());
        assertThat(saved.getValue().getReason()).isEqualTo(HintReportReason.WRONG_YEAR);
    }

    @Test
    void aRoundWithoutHintHasNoHintToReport() {
        finishedPlay();

        assertThatThrownBy(() -> handler.handleDaily(guest, TODAY, POSITION, hintReport(HintReportReason.FALSE)))
                .extracting("errorCode")
                .isEqualTo("NOTHING_TO_REPORT");
        verify(hintReports, never()).save(any());
    }

    @Test
    void aHintRejectedSinceIsNoLongerReported() {
        Hint hint = hint();
        hint.setReviewStatus(HintReviewStatus.REJECTED);
        finishedPlayWithHint(hint);

        assertThatThrownBy(() -> handler.handleDaily(guest, TODAY, POSITION, hintReport(HintReportReason.FALSE)))
                .extracting("errorCode")
                .isEqualTo("NOTHING_TO_REPORT");
    }

    @Test
    void alertsTheAdminWhenThePhotoReachesTheThreshold() {
        finishedPlay();
        openPhotoReports(3, 1);

        handler.handleDaily(guest, TODAY, POSITION, photo(PhotoReportReason.WRONG_PERSON));

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emails).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("admin@ageguessr.test");
        assertThat(sent.getValue().subject()).contains("Photo reported", "Jane Doe");
        assertThat(sent.getValue().htmlBody()).contains("3 open report(s)", "WRONG_PERSON", "/en/admin/curation");
    }

    @Test
    void doesNotAlertAgainPastTheThreshold() {
        finishedPlay();
        openPhotoReports(4, 2);

        handler.handleDaily(guest, TODAY, POSITION, photo(PhotoReportReason.WRONG_PERSON));

        verify(emails, never()).send(any());
    }

    @Test
    void alertsAtTheFirstReportOfAPhotoInTodaysGame() {
        finishedPlay();
        openPhotoReports(1, 1);
        when(dailyRounds.existsByGameDateAndPhotoId(TODAY, round.photo().getId())).thenReturn(true);

        handler.handleDaily(guest, TODAY, POSITION, photo(PhotoReportReason.TOO_SMALL));

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emails).send(sent.capture());
        assertThat(sent.getValue().htmlBody()).contains("today's game");
    }

    @Test
    void alertsAtTheFirstReportOfAMinor() {
        finishedPlay();
        openPhotoReports(2, 1);

        handler.handleDaily(guest, TODAY, POSITION, photo(PhotoReportReason.LOOKS_MINOR));

        verify(emails).send(any());
    }

    @Test
    void alertsAtTheFirstReportOfAHintOfTodaysGameOnly() {
        Hint hint = hint();
        finishedPlayWithHint(hint);
        when(hintReports.countByHintIdAndStatus(hint.getId(), ReportStatus.OPEN)).thenReturn(1L);

        handler.handleDaily(guest, TODAY, POSITION, hintReport(HintReportReason.UNCLEAR));

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emails).send(sent.capture());
        assertThat(sent.getValue().subject()).contains("Hint reported", "MUSIC");
        assertThat(sent.getValue().htmlBody()).contains("Hint 0", "UNCLEAR", "/en/admin/hints");
    }

    @Test
    void aRoundOfAnotherDayIsReportedButDoesNotAlertAtOnce() {
        LocalDate yesterday = TODAY.minusDays(1);
        when(rounds.of(yesterday, POSITION)).thenReturn(round);
        GamePlay play = GamePlay.start(yesterday, POSITION, guest, Language.EN, AGE, AGE, NOW);
        when(dailyPlays.findOf(guest, yesterday, POSITION)).thenReturn(Optional.of(play));
        openPhotoReports(1, 1);

        handler.handleDaily(guest, yesterday, POSITION, photo(PhotoReportReason.OTHER));

        verify(photoReports).save(any());
        verify(emails, never()).send(any());
    }

    @Test
    void reportsTheRoundOfASeries() {
        UUID seriesId = UUID.randomUUID();
        FreeSeries found = mock(FreeSeries.class);
        when(series.ofPlayer(seriesId, guest)).thenReturn(found);
        when(rounds.of(found, POSITION)).thenReturn(round);
        FreeSeriesPlay play = FreeSeriesPlay.start(seriesId, POSITION, guest, Language.EN, AGE, AGE, NOW);
        when(seriesPlays.findOf(guest, seriesId, POSITION)).thenReturn(Optional.of(play));
        openPhotoReports(1, 1);

        handler.handleSeries(guest, seriesId, POSITION, photo(PhotoReportReason.NOT_IDENTIFIABLE));

        assertThat(play.isPhotoReported()).isTrue();
        verify(seriesPlays).saveAndFlush(play);
        verify(photoReports).save(any());
        verify(emails, never()).send(any());
    }

    @Test
    void anUnknownSeriesIsNotFound() {
        UUID seriesId = UUID.randomUUID();
        when(series.ofPlayer(seriesId, guest)).thenThrow(new NotFoundException("No such series"));

        assertThatThrownBy(() -> handler.handleSeries(guest, seriesId, POSITION, photo(PhotoReportReason.OTHER)))
                .isInstanceOf(NotFoundException.class);
        verify(photoReports, never()).save(any());
    }
}
