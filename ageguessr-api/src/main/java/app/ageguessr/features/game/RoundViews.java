package app.ageguessr.features.game;

import app.ageguessr.features.hints.HintCategory;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Builds what a player sees of a round, from the round and their play of it. */
@Component
@RequiredArgsConstructor
public class RoundViews {

    static final String IMAGE_PATH = "/api/images/";

    private final HintPicker hints;

    /** @param play null when the player has not started the round */
    public RoundView of(GameRound round, GamePlay play) {
        RoundStep step = play == null ? RoundStep.NOT_STARTED : play.step();
        if (!round.playable()) {
            return new RoundView(round.position(), round.status(), null, step, null, null, null, null, null);
        }
        String imageUrl = IMAGE_PATH + round.photo().getPublicId();
        return switch (step) {
            case NOT_STARTED -> new RoundView(round.position(), round.status(), imageUrl, step, null, null, null, null, null);
            case FINISHED -> new RoundView(
                    round.position(), round.status(), imageUrl, step, play.getFirstGuess(), null, null, null, reveal(round, play));
            case AWAITING_HINT, AWAITING_SECOND_GUESS -> inProgress(round, play, imageUrl);
        };
    }

    /** Whether the player has a hint to choose before the second guess. */
    public boolean anyCategoryAvailable(GameRound round, GamePlay play) {
        return !offer(round, play.getLanguage()).categories().isEmpty();
    }

    /**
     * Without any category to offer, the round goes straight to the second guess: the
     * player is never stuck on a hint they cannot get.
     */
    private RoundView inProgress(GameRound round, GamePlay play, String imageUrl) {
        Direction direction = Direction.of(play.getFirstGuess(), round.age());
        if (play.step() == RoundStep.AWAITING_HINT) {
            List<HintCategory> categories = offer(round, play.getLanguage()).categories();
            RoundStep step = categories.isEmpty() ? RoundStep.AWAITING_SECOND_GUESS : RoundStep.AWAITING_HINT;
            return new RoundView(
                    round.position(), round.status(), imageUrl, step, play.getFirstGuess(), direction, categories, null, null);
        }
        RoundView.ShownHint hint = hints.shown(play.getHintId(), play.getLanguage())
                .map(shown -> new RoundView.ShownHint(shown.category(), shown.text()))
                .orElse(null);
        return new RoundView(
                round.position(),
                round.status(),
                imageUrl,
                RoundStep.AWAITING_SECOND_GUESS,
                play.getFirstGuess(),
                direction,
                null,
                hint,
                null);
    }

    private RoundReveal reveal(GameRound round, GamePlay play) {
        GameHint hint = hints.shown(play.getHintId(), play.getLanguage()).orElse(null);
        return RoundReveal.of(play, round.photo(), round.person(), hint);
    }

    /** The hints the round can show in this language, never citing its own person. */
    public HintOffer offer(GameRound round, Language language) {
        return hints.offerFor(round.gameDate(), round.age(), language, round.person().getWikidataId());
    }
}
