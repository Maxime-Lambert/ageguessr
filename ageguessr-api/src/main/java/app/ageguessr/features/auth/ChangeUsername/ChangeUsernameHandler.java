package app.ageguessr.features.auth.ChangeUsername;

import app.ageguessr.features.auth.ProfileResponse;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChangeUsernameHandler {

    private final UserRepository userRepository;

    public ProfileResponse handle(UUID userId, ChangeUsernameCommand command) {
        User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));

        // "IdNot": changing only the casing of one's own username (max -> Max) must not
        // collide with oneself under the case-insensitive uniqueness rule.
        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(command.username(), userId)) {
            throw new ConflictException("USERNAME_TAKEN", "This username is already taken");
        }

        user.setUsername(command.username());
        return ProfileResponse.from(userRepository.save(user));
    }
}
