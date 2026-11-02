package app.ageguessr.features.game;

import app.ageguessr.features.hints.HintCategory;
import java.util.List;
import lombok.Builder;

/**
 * A round as one player sees it, the only shape a round leaves the API in. Until the
 * round is finished it holds nothing that gives the answer away: no age, no identity, no
 * credit (the Commons page names the person), no internal photo id, and no hint year
 * (year of the game minus the age). The image goes out by its public id only.
 *
 * @param imageUrl null when the round is neutralized, the photo being out for everyone
 * @param firstGuess the player's own guess, once made
 * @param direction after a wrong first guess, until the result
 * @param categories the hint categories to choose from, while awaiting the hint
 * @param hint the hint chosen, while awaiting the second guess
 * @param result once finished, and only then
 */
@Builder
public record RoundView(
        short position,
        DailyRoundStatus status,
        String imageUrl,
        RoundStep step,
        Integer firstGuess,
        Direction direction,
        List<HintCategory> categories,
        ShownHint hint,
        RoundReveal result) {

    /** The hint during the round: its year would give the answer away, so it waits for the result. */
    public record ShownHint(HintCategory category, String text) {}
}
