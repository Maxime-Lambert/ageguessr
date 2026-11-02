package app.ageguessr.features.game;

/**
 * Points of a guess: up to {@value #MAX_PER_GUESS}, falling off with the gap to the real
 * age, round(500 × e^(−gap/10)). 1 year off gives 452, 5 give 303, 10 give 184, 20 give 68.
 * A starting point to tune with playtests (decision of 2026-11-02).
 */
public final class Scoring {

    public static final int MAX_PER_GUESS = 500;

    /** An exact first guess ends the round with the points of both guesses. */
    public static final int EXACT_FIRST_GUESS_ROUND = 2 * MAX_PER_GUESS;

    private static final double DECAY_YEARS = 10.0;

    private Scoring() {}

    public static int points(int guess, int age) {
        int gap = Math.abs(guess - age);
        return (int) Math.round(MAX_PER_GUESS * Math.exp(-gap / DECAY_YEARS));
    }
}
