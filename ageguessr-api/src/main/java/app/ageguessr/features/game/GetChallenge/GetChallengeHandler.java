package app.ageguessr.features.game.GetChallenge;

import app.ageguessr.features.game.ChallengeResponse;
import app.ageguessr.features.game.Challenges;
import app.ageguessr.features.game.PlayerKey;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A challenge by its code, for anyone with the link: its leaderboard only to who finished it. */
@Service
@RequiredArgsConstructor
public class GetChallengeHandler {

    private final Challenges challenges;

    @Transactional(readOnly = true)
    public ChallengeResponse handle(Optional<PlayerKey> visitor, String code) {
        return challenges.of(challenges.byCode(code), visitor);
    }
}
