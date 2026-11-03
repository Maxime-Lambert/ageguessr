package app.ageguessr.features.game.JoinChallenge;

import app.ageguessr.features.game.ChallengeResponse;
import app.ageguessr.features.game.Challenges;
import app.ageguessr.features.game.FreeSeries;
import app.ageguessr.features.game.FreeSeriesPlayer;
import app.ageguessr.features.game.FreeSeriesPlayerRepository;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.shared.exceptions.ValidationException;
import java.time.Clock;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes a visitor a player of a challenge, once: joining again changes nothing (not even
 * the nickname), so a player can never start over by joining twice.
 */
@Service
@RequiredArgsConstructor
public class JoinChallengeHandler {

    private final Challenges challenges;
    private final FreeSeriesPlayerRepository players;
    private final Clock clock;

    @Transactional
    public ChallengeResponse handle(PlayerKey player, String code, JoinChallengeCommand command) {
        FreeSeries shared = challenges.byCode(code);
        if (players.findOf(shared.getId(), player).isEmpty()) {
            String nickname = player.guestId() != null ? command.nickname() : null;
            if (player.guestId() != null && nickname == null) {
                throw new ValidationException("NICKNAME_REQUIRED", "A guest names themselves to join");
            }
            try {
                players.saveAndFlush(FreeSeriesPlayer.of(shared.getId(), player, nickname, clock.instant()));
            } catch (DataIntegrityViolationException e) {
                // Joined twice at once: the unique index of V15 lets one through, the
                // challenge is the same for both requests.
            }
        }
        return challenges.of(shared, Optional.of(player));
    }
}
