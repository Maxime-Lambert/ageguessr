package app.ageguessr.features.auth.ForgotPassword;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Same answer whether or not the email has an account - the response never confirms
 * an account exists. Unverified accounts get the email too: completing the reset proves
 * ownership of the address, which also verifies it.
 */
@Service
@RequiredArgsConstructor
public class ForgotPasswordHandler {

    private final UserRepository userRepository;
    private final AccountEmails accountEmails;

    public void handle(ForgotPasswordCommand command) {
        userRepository.findByEmailIgnoreCase(command.email()).ifPresent(accountEmails::sendPasswordReset);
    }
}
