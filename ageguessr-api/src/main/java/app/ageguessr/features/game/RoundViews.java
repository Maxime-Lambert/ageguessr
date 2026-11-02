package app.ageguessr.features.game;

import app.ageguessr.shared.i18n.Language;
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
        RoundView.RoundViewBuilder view =
                RoundView.builder().position(round.position()).status(round.status()).step(step);
        if (!round.playable()) {
            return view.build();
        }
        view.imageUrl(IMAGE_PATH + round.photo().getPublicId());
        return switch (step) {
            case NOT_STARTED -> view.build();
            case FINISHED -> view.firstGuess(play.getFirstGuess()).result(reveal(round, play)).build();
            case AWAITING_HINT, AWAITING_SECOND_GUESS -> inProgress(view, round, play);
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
    private RoundView inProgress(RoundView.RoundViewBuilder view, GameRound round, GamePlay play) {
        view.firstGuess(play.getFirstGuess()).direction(Direction.of(play.getFirstGuess(), round.age()));
        if (play.step() == RoundStep.AWAITING_HINT) {
            List<HintCategory> categories = offer(round, play.getLanguage()).categories();
            return view.step(categories.isEmpty() ? RoundStep.AWAITING_SECOND_GUESS : RoundStep.AWAITING_HINT)
                    .categories(categories)
                    .build();
        }
        return view.hint(hints.shown(play.getHintId(), play.getLanguage())
                        .map(shown -> new RoundView.ShownHint(shown.category(), shown.text()))
                        .orElse(null))
                .build();
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
