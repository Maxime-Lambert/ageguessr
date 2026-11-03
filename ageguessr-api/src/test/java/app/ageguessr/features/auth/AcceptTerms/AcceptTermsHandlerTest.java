package app.ageguessr.features.auth.AcceptTerms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.ProfileResponse;
import app.ageguessr.features.auth.TermsVersion;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AcceptTermsHandlerTest {

    private static final Instant NOW = Instant.parse("2026-11-04T10:00:00Z");
    private static final Instant BEFORE = Instant.parse("2026-01-01T10:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final AcceptTermsHandler handler = new AcceptTermsHandler(users, Clock.fixed(NOW, ZoneOffset.UTC));

    private User stored(String termsVersion, Instant acceptedAt) {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("a@x.com")
                .username("alice")
                .termsVersion(termsVersion)
                .termsAcceptedAt(acceptedAt)
                .build();
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(users.save(user)).thenReturn(user);
        return user;
    }

    @Test
    void recordsTheAcceptanceOfTheTermsInForce() {
        User user = stored("2026-01-01", BEFORE);
        assertThat(ProfileResponse.from(user).termsAccepted()).isFalse();

        ProfileResponse response = handler.handle(user.getId(), new AcceptTermsCommand(TermsVersion.CURRENT));

        assertThat(response.termsAccepted()).isTrue();
        assertThat(user.getTermsVersion()).isEqualTo(TermsVersion.CURRENT);
        assertThat(user.getTermsAcceptedAt()).isEqualTo(NOW);
    }

    @Test
    void anAccountThatNeverAcceptedAnyTermsIsAskedToo() {
        User user = stored(null, null);
        assertThat(ProfileResponse.from(user).termsAccepted()).isFalse();

        handler.handle(user.getId(), new AcceptTermsCommand(TermsVersion.CURRENT));

        assertThat(user.getTermsAcceptedAt()).isEqualTo(NOW);
    }

    @Test
    void acceptingAgainKeepsTheFirstDate() {
        User user = stored(TermsVersion.CURRENT, BEFORE);

        handler.handle(user.getId(), new AcceptTermsCommand(TermsVersion.CURRENT));

        assertThat(user.getTermsAcceptedAt()).isEqualTo(BEFORE);
    }

    @Test
    void refusesAVersionThatIsNotInForce() {
        User user = stored("2026-01-01", BEFORE);

        assertThatThrownBy(() -> handler.handle(user.getId(), new AcceptTermsCommand("2026-01-01")))
                .isInstanceOf(ConflictException.class)
                .extracting("errorCode")
                .isEqualTo(AcceptTermsHandler.TERMS_VERSION_OUTDATED);
        assertThat(user.getTermsAcceptedAt()).isEqualTo(BEFORE);
        verify(users, never()).save(any());
    }

    @Test
    void anUnknownAccountIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(users.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(unknown, new AcceptTermsCommand(TermsVersion.CURRENT)))
                .isInstanceOf(NotFoundException.class);
    }
}
