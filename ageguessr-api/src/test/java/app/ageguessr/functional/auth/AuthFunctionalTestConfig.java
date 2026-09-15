package app.ageguessr.functional.auth;

import app.ageguessr.shared.email.RecordingEmailSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Swaps in RecordingEmailSender for functional tests, regardless of active profile -
 * ResendEmailSender (@Profile("!e2e"), so active by default in tests) would otherwise
 * try a real network call to Resend. @Primary resolves the ambiguity between the two
 * EmailSender beans; declaring the return type as RecordingEmailSender (not the
 * EmailSender interface) also lets test classes @Autowired it directly to call
 * lastMessageTo(...).
 */
@TestConfiguration(proxyBeanMethods = false)
public class AuthFunctionalTestConfig {

    @Bean
    @Primary
    RecordingEmailSender emailSender() {
        return new RecordingEmailSender();
    }
}
