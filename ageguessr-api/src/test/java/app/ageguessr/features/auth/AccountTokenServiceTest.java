package app.ageguessr.features.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.shared.exceptions.NotFoundException;
import app.ageguessr.shared.security.OpaqueTokenGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountTokenServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private AccountTokenRepository accountTokenRepository;

    @Mock
    private UserRepository userRepository;

    private final OpaqueTokenGenerator tokenGenerator = new OpaqueTokenGenerator();

    private AccountTokenService service;

    @BeforeEach
    void setUp() {
        service = new AccountTokenService(accountTokenRepository, userRepository, tokenGenerator, FIXED_CLOCK);
    }

    @Test
    void issueStoresOnlyTheHashWithTheRequestedPurposeAndLifetime() {
        String raw = service.issue(USER_ID, AccountTokenPurpose.PASSWORD_RESET, Duration.ofHours(24));

        ArgumentCaptor<AccountToken> saved = ArgumentCaptor.forClass(AccountToken.class);
        verify(accountTokenRepository).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(tokenGenerator.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getValue().getPurpose()).isEqualTo(AccountTokenPurpose.PASSWORD_RESET);
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(FIXED_CLOCK.instant().plus(Duration.ofHours(24)));
    }

    @Test
    void redeemMarksTheTokenUsedAndReturnsItsUser() {
        AccountToken token = token(FIXED_CLOCK.instant().plusSeconds(60));
        User user = User.builder().id(USER_ID).build();
        when(accountTokenRepository.findByTokenHashAndPurpose(tokenGenerator.hash("raw"), AccountTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(accountTokenRepository.markConsumed(token.getId(), FIXED_CLOCK.instant())).thenReturn(1);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThat(service.redeem("raw", AccountTokenPurpose.PASSWORD_RESET)).isSameAs(user);
    }

    @Test
    void redeemRejectsAnUnknownOrWrongPurposeToken() {
        // Wrong purpose = not found: the lookup filters on purpose.
        when(accountTokenRepository.findByTokenHashAndPurpose(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.redeem("raw", AccountTokenPurpose.ACCOUNT_DELETION))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void redeemRejectsAnExpiredTokenWithoutConsumingIt() {
        AccountToken token = token(FIXED_CLOCK.instant().minusSeconds(1));
        when(accountTokenRepository.findByTokenHashAndPurpose(any(), any())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.redeem("raw", AccountTokenPurpose.PASSWORD_RESET))
                .isInstanceOf(NotFoundException.class);
        verify(accountTokenRepository, never()).markConsumed(any(), any());
    }

    @Test
    void redeemRejectsATokenAlreadyUsed() {
        AccountToken token = token(FIXED_CLOCK.instant().plusSeconds(60));
        when(accountTokenRepository.findByTokenHashAndPurpose(any(), any())).thenReturn(Optional.of(token));
        when(accountTokenRepository.markConsumed(token.getId(), FIXED_CLOCK.instant())).thenReturn(0);

        assertThatThrownBy(() -> service.redeem("raw", AccountTokenPurpose.PASSWORD_RESET))
                .isInstanceOf(NotFoundException.class);
        verify(userRepository, never()).findById(any());
    }

    private AccountToken token(Instant expiresAt) {
        return AccountToken.builder().id(UUID.randomUUID()).userId(USER_ID).expiresAt(expiresAt).build();
    }
}
