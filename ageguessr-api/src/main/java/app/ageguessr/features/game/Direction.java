package app.ageguessr.features.game;

/**
 * What the player learns after a wrong first guess: only the side, never how far
 * (docs/decisions/architecture.md, « Déroulé d'une manche »).
 */
public enum Direction {
    /** The person is older than the guess. */
    OLDER,
    YOUNGER;

    public static Direction of(int guess, int age) {
        if (guess == age) {
            throw new IllegalArgumentException("An exact guess has no direction");
        }
        return age > guess ? OLDER : YOUNGER;
    }
}
