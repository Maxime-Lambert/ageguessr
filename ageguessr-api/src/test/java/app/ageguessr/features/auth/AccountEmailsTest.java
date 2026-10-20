package app.ageguessr.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.EmailSender;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountEmailsTest {

    private static final User USER = User.builder().id(UUID.randomUUID()).email("me@x.com").build();

    @Mock
    private AccountTokenService accountTokenService;

    @Mock
    private EmailSender emailSender;

    private AccountEmails accountEmails;

    @BeforeEach
    void setUp() {
        accountEmails = new AccountEmails(accountTokenService, emailSender, "https://ageguessr.app", 60, 24, 60);
    }

    @Test
    void theResetEmailCarriesAOneHourResetLink() {
        when(accountTokenService.issue(USER.getId(), AccountTokenPurpose.PASSWORD_RESET, Duration.ofMinutes(60)))
                .thenReturn("raw-token");

        accountEmails.sendPasswordReset(USER);

        EmailMessage message = sent();
        assertThat(message.to()).isEqualTo("me@x.com");
        assertThat(message.htmlBody())
                .contains("https://ageguessr.app/reset-password?token=raw-token")
                .contains("expires in 1 hour");
    }

    @Test
    void theChangeNotificationCarriesA24HourSecureYourAccountLink() {
        when(accountTokenService.issue(USER.getId(), AccountTokenPurpose.PASSWORD_RESET, Duration.ofHours(24)))
                .thenReturn("raw-token");

        accountEmails.sendPasswordChanged(USER);

        EmailMessage message = sent();
        assertThat(message.subject()).isEqualTo("Your Ageguessr password was changed");
        assertThat(message.htmlBody())
                .contains("Secure your account")
                .contains("https://ageguessr.app/reset-password?token=raw-token")
                .contains("24 hours");
    }

    @Test
    void theDeletionEmailCarriesAOneHourDeletionLink() {
        when(accountTokenService.issue(USER.getId(), AccountTokenPurpose.ACCOUNT_DELETION, Duration.ofMinutes(60)))
                .thenReturn("raw-token");

        accountEmails.sendDeletionConfirmation(USER);

        assertThat(sent().htmlBody())
                .contains("https://ageguessr.app/confirm-account-deletion?token=raw-token")
                .contains("cannot be undone")
                .contains("expires in 1 hour");
    }

    private EmailMessage sent() {
        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(message.capture());
        return message.getValue();
    }
}
