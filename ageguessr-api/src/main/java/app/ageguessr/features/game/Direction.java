package app.ageguessr.features.game;

/**
 * The side of a wrong first guess, told with its {@link GapBand}
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
