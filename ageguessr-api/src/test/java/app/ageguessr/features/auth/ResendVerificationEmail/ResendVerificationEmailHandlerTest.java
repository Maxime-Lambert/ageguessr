package app.ageguessr.features.auth.ResendVerificationEmail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.ageguessr.features.auth.EmailVerificationService;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResendVerificationEmailHandlerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    private ResendVerificationEmailHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ResendVerificationEmailHandler(userRepository, emailVerificationService);
    }

    @Test
    void doesNothingButDoesNotFailForAnUnknownEmail() {
        when(userRepository.findByEmailIgnoreCase("ghost@x.com")).thenReturn(Optional.empty());

        handler.handle(new ResendVerificationEmailCommand("ghost@x.com"));

        verify(emailVerificationService, never()).issueAndSend(any());
    }

    @Test
    void doesNotSpamAnAlreadyVerifiedUser() {
        User verified = User.builder().email("verified@x.com").emailVerified(true).build();
        when(userRepository.findByEmailIgnoreCase("verified@x.com")).thenReturn(Optional.of(verified));

        handler.handle(new ResendVerificationEmailCommand("verified@x.com"));

        verify(emailVerificationService, never()).issueAndSend(any());
    }

    @Test
    void resendsForAnUnverifiedUser() {
        User unverified = User.builder().email("unverified@x.com").emailVerified(false).build();
        when(userRepository.findByEmailIgnoreCase("unverified@x.com")).thenReturn(Optional.of(unverified));

        handler.handle(new ResendVerificationEmailCommand("unverified@x.com"));

        verify(emailVerificationService).issueAndSend(unverified);
    }
}
