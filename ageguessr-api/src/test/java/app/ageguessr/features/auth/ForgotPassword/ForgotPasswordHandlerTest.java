package app.ageguessr.features.auth.ForgotPassword;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ForgotPasswordHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccountEmails accountEmails;

    @InjectMocks
    private ForgotPasswordHandler handler;

    @Test
    void sendsAResetEmailToAnExistingAccount() {
        User user = User.builder().email("me@x.com").emailVerified(false).build();
        when(userRepository.findByEmailIgnoreCase("ME@x.com")).thenReturn(Optional.of(user));

        handler.handle(new ForgotPasswordCommand("ME@x.com"));

        verify(accountEmails).sendPasswordReset(user);
    }

    @Test
    void silentlyDoesNothingForAnUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("ghost@x.com")).thenReturn(Optional.empty());

        handler.handle(new ForgotPasswordCommand("ghost@x.com"));

        verify(accountEmails, never()).sendPasswordReset(any());
    }
}
