package app.ageguessr.features.game.ShareSeries;

import app.ageguessr.features.game.ChallengeResponse;
import app.ageguessr.features.game.Challenges;
import app.ageguessr.features.game.FreeSeries;
import app.ageguessr.features.game.FreeSeriesPlayer;
import app.ageguessr.features.game.FreeSeriesPlayerRepository;
import app.ageguessr.features.game.FreeSeriesRepository;
import app.ageguessr.features.game.GameErrors;
import app.ageguessr.features.game.PlayerKey;
import app.ageguessr.features.game.SeriesViews;
import app.ageguessr.features.game.ShareCodes;
import app.ageguessr.shared.exceptions.ValidationException;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a series its creator has played into a challenge. Sharing again gives the same
 * code: the link already sent keeps working.
 */
@Service
@RequiredArgsConstructor
public class ShareSeriesHandler {

    /** 40 bits make a collision rare; a few tries make a failure practically impossible. */
    private static final int CODE_TRIES = 5;

    private final SeriesViews views;
    private final FreeSeriesRepository series;
    private final FreeSeriesPlayerRepository players;
    private final ShareCodes codes;
    private final Challenges challenges;
    private final Clock clock;

    @Transactional
    public ChallengeResponse handle(PlayerKey player, UUID seriesId, ShareSeriesCommand command) {
        FreeSeries found = views.ofPlayer(seriesId, player);
        if (!found.isCreatedBy(player)) {
            throw GameErrors.notCreator();
        }
        if (!found.shared()) {
            if (!views.of(found, player).score().complete()) {
                throw GameErrors.seriesNotFinished();
            }
            if (player.guestId() != null) {
                if (command.nickname() == null) {
                    throw new ValidationException("NICKNAME_REQUIRED", "A guest names themselves to share");
                }
                FreeSeriesPlayer creator = players.findOf(seriesId, player).orElseThrow();
                creator.name(command.nickname());
            }
            found.share(freeCode(), clock.instant());
            series.flush();
        }
        return challenges.of(found, Optional.of(player));
    }

    private String freeCode() {
        for (int i = 0; i < CODE_TRIES; i++) {
            String code = codes.next();
            if (series.findByShareCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("No free share code after " + CODE_TRIES + " tries");
    }
}
