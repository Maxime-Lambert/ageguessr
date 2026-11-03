package app.ageguessr.features.auth.AcceptTerms;

import app.ageguessr.features.auth.ProfileResponse;
import app.ageguessr.features.auth.TermsVersion;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A signed-in player accepts the Terms in force, after they changed. Only the version in
 * force: a page loaded before a change would otherwise record a version the player never
 * read as the current one.
 */
@Service
@RequiredArgsConstructor
public class AcceptTermsHandler {

    static final String TERMS_VERSION_OUTDATED = "TERMS_VERSION_OUTDATED";

    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public ProfileResponse handle(UUID userId, AcceptTermsCommand command) {
        if (!TermsVersion.CURRENT.equals(command.version())) {
            throw new ConflictException(TERMS_VERSION_OUTDATED, "The Terms have changed since this page was loaded");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.acceptTerms(command.version(), clock.instant());
        return ProfileResponse.from(userRepository.save(user));
    }
}
