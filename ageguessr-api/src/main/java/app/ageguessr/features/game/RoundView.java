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
 * @param direction after a wrong first guess, until the result, as {@code gap} and {@code range}
 * @param gap how far the first guess is, by band: with the side, a span of 5 or 10 years
 * @param range the ages that span leaves, within 18-100, for the screen to keep the final guess in
 * @param categories the hint categories to choose from, while awaiting the hints
 * @param hintsToPick how many of them to choose, as {@code categories}
 * @param hints the hints of the categories chosen, while awaiting the second guess
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
        GapBand gap,
        GuessRange range,
        List<HintCategory> categories,
        Integer hintsToPick,
        List<ShownHint> hints,
        RoundReveal result) {

    /** The hint during the round: its year would give the answer away, so it waits for the result. */
    public record ShownHint(HintCategory category, String text) {}
}
