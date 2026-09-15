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
 * Test-only: lets the QA/Playwright journey retrieve a verification token without a
 * real inbox, by reading it back out of the last email RecordingEmailSender captured.
 * Gated by @Profile("e2e") - MUST NEVER be active in the prod deployment config. Its
 * path is also carved out as public only under that same profile, in
 * TestSupportSecurityConfig - never reachable at all otherwise.
 */
@RestController
@Profile("e2e")
@RequiredArgsConstructor
public class TestSupportController {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([^\"&]+)");

    private final RecordingEmailSender emailSender;

    @GetMapping("/test-support/verification-token")
    ResponseEntity<TokenResponse> verificationToken(@RequestParam String email) {
        EmailMessage message = emailSender.lastMessageTo(email);
        if (message == null) {
            return ResponseEntity.notFound().build();
        }
        Matcher matcher = TOKEN_PATTERN.matcher(message.htmlBody());
        if (!matcher.find()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new TokenResponse(matcher.group(1)));
    }

    record TokenResponse(String token) {}
}
