package app.ageguessr.features.auth.ConfirmAccountDeletion;

import app.ageguessr.features.auth.AccountTokenPurpose;
import app.ageguessr.features.auth.AccountTokenService;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard delete (right to erasure). Every table referencing users must be ON DELETE
 * CASCADE (or anonymize) - see docs/decisions/architecture.md - so this one delete is
 * the whole job.
 */
@Service
@RequiredArgsConstructor
public class ConfirmAccountDeletionHandler {

    private final AccountTokenService accountTokenService;
    private final UserRepository userRepository;

    @Transactional
    public void handle(ConfirmAccountDeletionCommand command) {
        User user = accountTokenService.redeem(command.token(), AccountTokenPurpose.ACCOUNT_DELETION);
        userRepository.delete(user);
    }
}
