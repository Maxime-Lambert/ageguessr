package app.ageguessr.features.game.ReportRound;

import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.people.Person;
import app.ageguessr.features.people.PhotoReportReason;
import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

/**
 * Tells the admin by email when a photo or a hint needs a look. Once per crossing: the
 * email goes out when the count of open reports reaches the threshold, not at each report
 * after it. Sooner when it is urgent: a photo of today's game, which every player sees,
 * or one that looks like a minor's, which must never be shown (V8).
 *
 * <p>Sent after the commit, so that a report rolled back never alerts; a failure to send
 * is logged and never fails the report. Without an admin address, a log line instead.
 */
@Slf4j
@Component
class ReportAlerts {

    private final EmailSender emailSender;
    private final String adminEmail;
    private final String frontendUrl;
    private final int threshold;

    ReportAlerts(
            EmailSender emailSender,
            @Value("${app.admin.email:}") String adminEmail,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.reports.alert-threshold}") int threshold) {
        this.emailSender = emailSender;
        this.adminEmail = adminEmail;
        this.frontendUrl = frontendUrl;
        this.threshold = threshold;
    }

    /**
     * @param open the open reports of the photo, this one included
     * @param openForReason those of them with the reason of this one
     */
    boolean photoDue(long open, long openForReason, PhotoReportReason reason, boolean inTodaysGame) {
        return open == threshold
                || (inTodaysGame && open == 1)
                || (reason == PhotoReportReason.LOOKS_MINOR && openForReason == 1);
    }

    /** @param fromTodaysGame reported from a round of today's game, whose hints everyone sees */
    boolean hintDue(long open, boolean fromTodaysGame) {
        return open == threshold || (fromTodaysGame && open == 1);
    }

    void photo(Person person, PhotoReportReason reason, long open, boolean inTodaysGame) {
        String name = person.getNameEn() != null ? person.getNameEn() : person.getNameFr();
        String subject = "[Ageguessr] Photo reported: " + name;
        String body = "<p>A photo of <strong>" + HtmlUtils.htmlEscape(name) + "</strong> ("
                + HtmlUtils.htmlEscape(person.getWikidataId()) + ") has " + open + " open report(s), the last one: "
                + reason + ".</p>"
                + (inTodaysGame ? "<p><strong>It is in today's game.</strong></p>" : "")
                + "<p><a href=\"" + frontendUrl + "/en/admin/curation\">Open the photo curation</a></p>";
        send(subject, body);
    }

    void hint(Hint hint, String reason, long open, boolean fromTodaysGame) {
        String text = hint.getTextEn() != null ? hint.getTextEn() : hint.getTextFr();
        String subject = "[Ageguessr] Hint reported (" + hint.getCategory() + ", " + hint.getYear() + ")";
        String body = "<p>The hint <em>" + HtmlUtils.htmlEscape(text) + "</em> has " + open
                + " open report(s), the last one: " + reason + ".</p>"
                + (fromTodaysGame ? "<p><strong>It was reported from today's game.</strong></p>" : "")
                + "<p><a href=\"" + frontendUrl + "/en/admin/hints\">Open the hint curation</a></p>";
        send(subject, body);
    }

    private void send(String subject, String body) {
        if (adminEmail.isBlank()) {
            log.warn("{} (no ADMIN_EMAIL, nothing sent)", subject);
            return;
        }
        Runnable sending = () -> {
            try {
                emailSender.send(new EmailMessage(adminEmail, subject, body));
            } catch (RuntimeException e) {
                log.error("Could not send the report alert: {}", subject, e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sending.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sending.run();
            }
        });
    }
}
