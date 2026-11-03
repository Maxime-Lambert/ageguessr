package app.ageguessr;

import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.RecordingEmailSender;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only: lets the QA/Playwright journeys read the last email sent to an address
 * (verification, password reset, account deletion) without a real inbox: its subject and
 * the link it carries, read back out of what RecordingEmailSender captured. The journeys
 * follow that link as is, so they also check its language prefix (/en/ or /fr/).
 * Gated by @Profile("e2e") - MUST NEVER be active in the prod deployment config. Its
 * path is also carved out as public only under that same profile, in
 * TestSupportSecurityConfig - never reachable at all otherwise.
 */
@RestController
@Profile("e2e")
@RequiredArgsConstructor
public class TestSupportController {

    private static final Pattern LINK_PATTERN = Pattern.compile("href=\"([^\"]+)\"");

    private final RecordingEmailSender emailSender;

    @GetMapping("/test-support/last-email")
    ResponseEntity<LastEmailResponse> lastEmail(@RequestParam String email) {
        EmailMessage message = emailSender.lastMessageTo(email);
        if (message == null) {
            return ResponseEntity.notFound().build();
        }
        Matcher matcher = LINK_PATTERN.matcher(message.htmlBody());
        if (!matcher.find()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new LastEmailResponse(message.subject(), matcher.group(1)));
    }

    record LastEmailResponse(String subject, String link) {}
}
