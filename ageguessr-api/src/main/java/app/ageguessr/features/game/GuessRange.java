package app.ageguessr.features.game;

/**
 * The ages left after a wrong first guess, from its side and its band, within the ages a
 * guess may take: the screen keeps the final guess inside it. It holds the answer, so it
 * never leaves the API empty or narrower than the band.
 */
public record GuessRange(int min, int max) {

    public static final int MIN_AGE = 18;
    public static final int MAX_AGE = 100;

    public static GuessRange of(int guess, int age) {
        GapBand band = GapBand.of(guess, age);
        int near = band.minGap();
        // FAR has no upper bound: its span stops at the edge of the guess range.
        long far = band.maxGap();
        if (Direction.of(guess, age) == Direction.OLDER) {
            return bounded(guess + near, guess + far);
        }
        return bounded(guess - far, guess - near);
    }

    /**
     * Clamped to 18-100. The database allows a photo up to 120 years old, out of reach of a
     * guess: the range then shrinks to the last age a guess can take rather than be empty.
     */
    private static GuessRange bounded(long low, long high) {
        int min = (int) Math.min(MAX_AGE, Math.max(MIN_AGE, low));
        int max = (int) Math.min(MAX_AGE, Math.max(MIN_AGE, high));
        return new GuessRange(min, max);
    }
}
