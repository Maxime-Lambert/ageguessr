package app.ageguessr.features.auth.SetUpTwoFactor;

import app.ageguessr.features.auth.CurrentPasswordVerifier;
import app.ageguessr.features.auth.SecretCipher;
import app.ageguessr.features.auth.Totp;
import app.ageguessr.features.auth.User;
import app.ageguessr.features.auth.UserRepository;
import app.ageguessr.shared.exceptions.ConflictException;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * First step of turning two-factor on: a new secret, kept pending (encrypted) until a
 * first code proves the app has it (EnableTwoFactor). Asks for the password, like every
 * sensitive action; starting again replaces a pending secret. Not @Transactional: a wrong
 * password must stay counted (CurrentPasswordVerifier).
 */
@Service
@RequiredArgsConstructor
public class SetUpTwoFactorHandler {

    private final UserRepository users;
    private final CurrentPasswordVerifier passwordVerifier;
    private final SecretCipher cipher;

    public SetUpTwoFactorResponse handle(UUID userId, SetUpTwoFactorCommand command) {
        User user = users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
        if (user.hasTwoFactor()) {
            throw new ConflictException("TWO_FACTOR_ALREADY_ENABLED", "Two-factor authentication is already on");
        }
        passwordVerifier.verify(user, command.password());
        String secret = Totp.newSecret();
        user.setTotpSecret(cipher.encrypt(secret));
        user.setTotpLastStep(null);
        users.save(user);
        return new SetUpTwoFactorResponse(secret, Totp.otpauthUri(user.getUsername(), secret));
    }
}
