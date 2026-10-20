package app.ageguessr.features.auth.DeleteAccount;

import app.ageguessr.features.auth.AccountEmails;
import app.ageguessr.features.auth.CurrentPasswordVerifier;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * First step of the deletion: the password alone is not enough to do the one thing
 * that cannot be recovered from, the emailed link must be confirmed too (see
 * ConfirmAccountDeletionHandler). Not @Transactional, for the same reason as
 * ChangePasswordHandler (failed attempts must stick).
 */
@Service
@RequiredArgsConstructor
public class DeleteAccountHandler {

    private final UserRepository userRepository;
    private final CurrentPasswordVerifier currentPasswordVerifier;
    private final AccountEmails accountEmails;

    public void handle(UUID userId, DeleteAccountCommand command) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        currentPasswordVerifier.verify(user, command.password());
        userRepository.save(user);

        accountEmails.sendDeletionConfirmation(user);
    }
}
