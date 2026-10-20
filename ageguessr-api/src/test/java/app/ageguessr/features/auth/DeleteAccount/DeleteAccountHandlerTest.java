package app.ageguessr.features.auth.DeleteAccount;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.CurrentPasswordVerifier;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ForbiddenException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeleteAccountHandlerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentPasswordVerifier currentPasswordVerifier;

    @Mock
    private AccountEmails accountEmails;

    @InjectMocks
    private DeleteAccountHandler handler;

    @Test
    void theRightPasswordOnlySendsAConfirmationEmail() {
        User user = User.builder().id(USER_ID).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        handler.handle(USER_ID, new DeleteAccountCommand("right"));

        verify(accountEmails).sendDeletionConfirmation(user);
        verify(userRepository, never()).delete(any());
    }

    @Test
    void aWrongPasswordSendsNothing() {
        User user = User.builder().id(USER_ID).build();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        doThrow(new ForbiddenException("INVALID_CURRENT_PASSWORD", "nope"))
                .when(currentPasswordVerifier)
                .verify(user, "wrong");

        assertThatThrownBy(() -> handler.handle(USER_ID, new DeleteAccountCommand("wrong")))
                .isInstanceOf(ForbiddenException.class);

        verify(accountEmails, never()).sendDeletionConfirmation(any());
    }
}
