package app.ageguessr.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.shared.email.EmailMessage;
import app.ageguessr.shared.email.EmailSender;
import app.ageguessr.shared.i18n.Language;
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

    /** Never chose a language (accounts created before V14): English. */
    private static final User USER = User.builder().id(UUID.randomUUID()).email("me@x.com").build();

    private static final User FRENCH_USER = User.builder()
            .id(UUID.randomUUID())
            .email("moi@x.com")
            .language(Language.FR)
            .build();

    @Mock
    private AccountTokenService accountTokenService;

    @Mock
    private EmailSender emailSender;

    private AccountEmails accountEmails;

    @BeforeEach
    void setUp() {
        accountEmails = new AccountEmails(
                accountTokenService, emailSender, new AccountLinks("https://ageguessr.app"), 60, 24, 60);
    }

    @Test
    void theResetEmailCarriesAOneHourResetLink() {
        when(accountTokenService.issue(USER.getId(), AccountTokenPurpose.PASSWORD_RESET, Duration.ofMinutes(60)))
                .thenReturn("raw-token");

        accountEmails.sendPasswordReset(USER);

        EmailMessage message = sent();
        assertThat(message.to()).isEqualTo("me@x.com");
        assertThat(message.htmlBody())
                .contains("https://ageguessr.app/en/reset-password?token=raw-token")
                .contains("expires in 1 hour");
    }

    @Test
    void theChangeNotificationCarriesA24HourSecureYourAccountLink() {
        when(accountTokenService.issue(USER.getId(), AccountTokenPurpose.PASSWORD_RESET, Duration.ofHours(24)))
                .thenReturn("raw-token");

        accountEmails.sendPasswordChanged(USER);

        EmailMessage message = sent();
        assertThat(message.subject()).isEqualTo("Your AgeGuessr password was changed");
        assertThat(message.htmlBody())
                .contains("Secure your account")
                .contains("https://ageguessr.app/en/reset-password?token=raw-token")
                .contains("24 hours");
    }

    @Test
    void theDeletionEmailCarriesAOneHourDeletionLink() {
        when(accountTokenService.issue(USER.getId(), AccountTokenPurpose.ACCOUNT_DELETION, Duration.ofMinutes(60)))
                .thenReturn("raw-token");

        accountEmails.sendDeletionConfirmation(USER);

        assertThat(sent().htmlBody())
                .contains("https://ageguessr.app/en/confirm-account-deletion?token=raw-token")
                .contains("cannot be undone")
                .contains("expires in 1 hour");
    }

    @Test
    void writesInFrenchToAFrenchAccountWithLinksToTheFrenchSite() {
        when(accountTokenService.issue(FRENCH_USER.getId(), AccountTokenPurpose.PASSWORD_RESET, Duration.ofMinutes(60)))
                .thenReturn("raw-token");

        accountEmails.sendPasswordReset(FRENCH_USER);

        EmailMessage message = sent();
        assertThat(message.subject()).isEqualTo("Réinitialisez votre mot de passe AgeGuessr");
        assertThat(message.htmlBody())
                .contains("https://ageguessr.app/fr/reset-password?token=raw-token")
                .contains("expire dans 1 heure");
    }

    @Test
    void writesEveryAccountEmailInFrench() {
        when(accountTokenService.issue(FRENCH_USER.getId(), AccountTokenPurpose.PASSWORD_RESET, Duration.ofHours(24)))
                .thenReturn("raw-token");
        when(accountTokenService.issue(FRENCH_USER.getId(), AccountTokenPurpose.ACCOUNT_DELETION, Duration.ofMinutes(60)))
                .thenReturn("raw-token");

        accountEmails.sendPasswordChanged(FRENCH_USER);
        accountEmails.sendDeletionConfirmation(FRENCH_USER);

        ArgumentCaptor<EmailMessage> messages = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(2)).send(messages.capture());
        assertThat(messages.getAllValues().get(0).htmlBody())
                .contains("Sécurisez votre compte")
                .contains("https://ageguessr.app/fr/reset-password?token=raw-token")
                .contains("24 heures");
        assertThat(messages.getAllValues().get(1).subject())
                .isEqualTo("Confirmez la suppression de votre compte AgeGuessr");
        assertThat(messages.getAllValues().get(1).htmlBody())
                .contains("https://ageguessr.app/fr/confirm-account-deletion?token=raw-token")
                .contains("C'est définitif");
    }

    private EmailMessage sent() {
        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(message.capture());
        return message.getValue();
    }
}
