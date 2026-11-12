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
    private final HintProperties hintProperties;

    /** @param play null when the player has not started the round */
    public RoundView of(GameRound round, PlayedRound play) {
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
    public boolean anyCategoryAvailable(GameRound round, PlayedRound play) {
        return !offer(round, play.getLanguage()).categories().isEmpty();
    }

    /**
     * Without any category to offer, the round goes straight to the second guess: the
     * player is never stuck on a hint they cannot get.
     */
    private RoundView inProgress(RoundView.RoundViewBuilder view, GameRound round, PlayedRound play) {
        int guess = play.getFirstGuess();
        view.firstGuess(guess)
                .direction(Direction.of(guess, round.age()))
                .gap(GapBand.of(guess, round.age()))
                .range(GuessRange.of(guess, round.age()));
        if (play.step() == RoundStep.AWAITING_HINT) {
            HintOffer offer = offer(round, play.getLanguage());
            List<HintCategory> categories = offer.categories();
            if (categories.isEmpty()) {
                return view.step(RoundStep.AWAITING_SECOND_GUESS).hints(List.of()).build();
            }
            return view.categories(categories)
                    .hintsToPick(offer.required(hintProperties.perRound()))
                    .build();
        }
        return view.hints(hints.shown(play.getHintIds(), play.getLanguage()).stream()
                        .map(shown -> new RoundView.ShownHint(shown.category(), shown.text()))
                        .toList())
                .build();
    }

    private RoundReveal reveal(GameRound round, PlayedRound play) {
        return RoundReveal.of(play, round.photo(), round.person(), hints.shown(play.getHintIds(), play.getLanguage()));
    }

    /** How many categories a player picks in this round, fewer when fewer are offered. */
    public int hintsPerRound() {
        return hintProperties.perRound();
    }

    /** The hints the round can show in this language, never citing its own person. */
    public HintOffer offer(GameRound round, Language language) {
        return hints.offerFor(round.gameDate(), round.age(), language, round.person().getWikidataId());
    }
}
