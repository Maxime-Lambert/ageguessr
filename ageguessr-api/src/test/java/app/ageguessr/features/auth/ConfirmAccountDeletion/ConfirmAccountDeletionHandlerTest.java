package app.ageguessr.features.auth.ConfirmAccountDeletion;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.AccountTokenPurpose;
import app.ageguessr.features.auth.AccountTokenService;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfirmAccountDeletionHandlerTest {

    @Mock
    private AccountTokenService accountTokenService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ConfirmAccountDeletionHandler handler;

    @Test
    void aValidDeletionTokenDeletesTheAccount() {
        User user = User.builder().build();
        when(accountTokenService.redeem("raw", AccountTokenPurpose.ACCOUNT_DELETION)).thenReturn(user);

        handler.handle(new ConfirmAccountDeletionCommand("raw"));

        verify(userRepository).delete(user);
    }

    @Test
    void anInvalidTokenDeletesNothing() {
        when(accountTokenService.redeem("bad", AccountTokenPurpose.ACCOUNT_DELETION))
                .thenThrow(new NotFoundException("invalid"));

        assertThatThrownBy(() -> handler.handle(new ConfirmAccountDeletionCommand("bad")))
                .isInstanceOf(NotFoundException.class);
        verify(userRepository, never()).delete(any());
    }
}
