package app.ageguessr.features.auth.PurgeAccounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.AccountRetentionProperties;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurgeAccountsHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-15T00:50:00Z");

    @Mock
    private UserRepository users;

    @Mock
    private AccountEmails emails;

    private PurgeAccountsHandler handler() {
        return new PurgeAccountsHandler(
                users, emails, new AccountRetentionProperties(7, 1096, 30, true), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void asksTheRepositoryWithTheDurationsAndAnnouncesTheDateOfDeletion() {
        User user = User.builder().id(UUID.randomUUID()).build();
        when(users.deleteUnverifiedCreatedBefore(NOW.minus(Duration.ofDays(7)))).thenReturn(2);
        when(users.deleteInactive(
                        NOW.minus(Duration.ofDays(1096)), NOW.minus(Duration.ofDays(30)).minus(Duration.ofHours(12))))
                .thenReturn(1);
        when(users.findInactiveToWarn(NOW.minus(Duration.ofDays(1066)))).thenReturn(List.of(user));

        PurgeAccountsHandler.Result result = handler().purge();

        assertThat(result).isEqualTo(new PurgeAccountsHandler.Result(2, 1, 1));
        // Deleted by the run 31 days later: the one 30 days later is a few hours short.
        verify(emails).sendInactivityWarning(user, NOW.plus(Duration.ofDays(31)));
        verify(users).markWarned(user.getId(), NOW);
    }

    @Test
    void anEmailThatFailsIsNotMarkedSentAndTheOthersStillGo() {
        User failing = User.builder().id(UUID.randomUUID()).build();
        User fine = User.builder().id(UUID.randomUUID()).build();
        when(users.findInactiveToWarn(any())).thenReturn(List.of(failing, fine));
        doThrow(new IllegalStateException("provider down")).when(emails).sendInactivityWarning(any(), any());
        doNothing().when(emails).sendInactivityWarning(fine, NOW.plus(Duration.ofDays(31)));

        PurgeAccountsHandler.Result result = handler().purge();

        assertThat(result.warned()).isEqualTo(1);
        verify(users, never()).markWarned(failing.getId(), NOW);
        verify(users).markWarned(fine.getId(), NOW);
    }
}
