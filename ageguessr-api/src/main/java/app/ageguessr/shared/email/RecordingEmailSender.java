package app.ageguessr.shared.email;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Test double: records messages in memory instead of sending them, keyed by
 * recipient (last message wins per address). Auto-registered as the EmailSender bean
 * only under the "e2e" Spring profile (used by the standalone backend process the
 * QA/Playwright journey runs against, via TestSupportController) — JUnit tests
 * instantiate it directly in a @TestConfiguration @Bean method instead, which bypasses
 * this @Profile restriction entirely since no component scanning is involved.
 */
@Component
@Profile("e2e")
public class RecordingEmailSender implements EmailSender {

    private final Map<String, EmailMessage> lastMessageByRecipient = new ConcurrentHashMap<>();

    @Override
    public void send(EmailMessage message) {
        lastMessageByRecipient.put(message.to(), message);
    }

    public EmailMessage lastMessageTo(String recipient) {
        return lastMessageByRecipient.get(recipient);
    }
}
