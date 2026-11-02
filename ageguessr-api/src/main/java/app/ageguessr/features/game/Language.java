package app.ageguessr.features.game;

import app.ageguessr.features.hints.HintAudience;
import java.util.List;

/**
 * Language of the hint and of the name revealed for a round, chosen by the browser and
 * fixed at the first guess. The rest of the interface stays in English.
 */
public enum Language {
    EN(HintAudience.EN),
    FR(HintAudience.FR);

    private final HintAudience audience;

    Language(HintAudience audience) {
        this.audience = audience;
    }

    /** The hints a player of this language can read: those for everyone, and their own. */
    public List<HintAudience> audiences() {
        return List.of(HintAudience.ALL, audience);
    }
}
