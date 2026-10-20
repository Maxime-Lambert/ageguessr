package app.ageguessr.features.auth.Me;

import app.ageguessr.features.auth.ProfileResponse;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MeHandler {

    private final UserRepository userRepository;

    public ProfileResponse handle(MeQuery query) {
        User user = userRepository
                .findById(query.userId())
                .orElseThrow(() -> new NotFoundException("User not found"));
        return ProfileResponse.from(user);
    }
}
