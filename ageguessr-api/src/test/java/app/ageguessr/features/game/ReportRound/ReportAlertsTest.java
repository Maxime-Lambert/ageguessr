package app.ageguessr.features.game.ReportRound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PhotoReportReason;
import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.EmailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReportAlertsTest {

    private final EmailSender emails = mock(EmailSender.class);
    private final ReportAlerts alerts = new ReportAlerts(emails, "admin@ageguessr.test", "https://ageguessr.app", 3);

    @Test
    void aPhotoIsDueAtTheThresholdOnly() {
        assertThat(alerts.photoDue(2, 2, PhotoReportReason.OTHER, false)).isFalse();
        assertThat(alerts.photoDue(3, 3, PhotoReportReason.OTHER, false)).isTrue();
        assertThat(alerts.photoDue(4, 4, PhotoReportReason.OTHER, false)).isFalse();
    }

    @Test
    void aPhotoOfTodaysGameIsDueAtItsFirstReport() {
        assertThat(alerts.photoDue(1, 1, PhotoReportReason.OTHER, true)).isTrue();
        assertThat(alerts.photoDue(2, 1, PhotoReportReason.OTHER, true)).isFalse();
    }

    @Test
    void aMinorIsDueAtItsFirstReportWhateverTheOthers() {
        assertThat(alerts.photoDue(2, 1, PhotoReportReason.LOOKS_MINOR, false)).isTrue();
        assertThat(alerts.photoDue(5, 2, PhotoReportReason.LOOKS_MINOR, false)).isFalse();
        assertThat(alerts.photoDue(2, 1, PhotoReportReason.TOO_SMALL, false)).isFalse();
    }

    @Test
    void aHintIsDueAtTheThresholdOrAtOnceFromTodaysGame() {
        assertThat(alerts.hintDue(1, false)).isFalse();
        assertThat(alerts.hintDue(3, false)).isTrue();
        assertThat(alerts.hintDue(1, true)).isTrue();
        assertThat(alerts.hintDue(2, true)).isFalse();
    }

    @Test
    void escapesTheNameInTheEmail() {
        Person person = Person.builder().wikidataId("Q1").nameFr("<b>Jean</b>").build();

        alerts.photo(person, PhotoReportReason.OTHER, 3, false);

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emails).send(sent.capture());
        assertThat(sent.getValue().htmlBody()).contains("&lt;b&gt;Jean&lt;/b&gt;").doesNotContain("<b>Jean");
        assertThat(sent.getValue().htmlBody()).doesNotContain("today's game");
    }

    @Test
    void keepsTheSubjectOnOneLine() {
        alerts.photo(Person.builder().wikidataId("Q1").nameEn("Jane\r\nBcc: x@y.z").build(),
                PhotoReportReason.OTHER, 3, false);

        ArgumentCaptor<EmailMessage> sent = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emails).send(sent.capture());
        assertThat(sent.getValue().subject()).doesNotContain("\r", "\n");
    }

    @Test
    void sendsNothingWithoutAnAdminAddress() {
        ReportAlerts silent = new ReportAlerts(emails, "", "https://ageguessr.app", 3);

        silent.photo(Person.builder().wikidataId("Q1").nameEn("Jane").build(), PhotoReportReason.OTHER, 3, false);

        verify(emails, never()).send(any());
    }

    @Test
    void aFailureToSendNeverFailsTheReport() {
        doThrow(new IllegalStateException("Resend down")).when(emails).send(any());

        alerts.photo(Person.builder().wikidataId("Q1").nameEn("Jane").build(), PhotoReportReason.OTHER, 3, false);

        verify(emails).send(any());
    }
}
