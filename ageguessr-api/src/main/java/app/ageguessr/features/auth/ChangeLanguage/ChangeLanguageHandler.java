package app.ageguessr.features.auth.ChangeLanguage;

import app.ageguessr.features.auth.ProfileResponse;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** The language of the account: of its emails, and of the site once signed in. */
@Service
@RequiredArgsConstructor
public class ChangeLanguageHandler {

    private final UserRepository userRepository;

    public ProfileResponse handle(UUID userId, ChangeLanguageCommand command) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        user.setLanguage(command.language());
        return ProfileResponse.from(userRepository.save(user));
    }
}
