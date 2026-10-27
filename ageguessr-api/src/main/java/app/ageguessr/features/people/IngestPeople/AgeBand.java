package app.ageguessr.features.people.IngestPeople;

/**
 * Bands of 8 years of age on a photo, from 18: "18-25", "26-33"... and "82+" for the
 * rest (people born since 1930 are at most 96 on a photo). Used to see how the pool
 * spreads across ages.
 */
final class AgeBand {

    static final int WIDTH = 8;
    static final int LAST_START = 82;

    private AgeBand() {}

    static String of(int age) {
        if (age < PhotoEvaluator.MIN_AGE) {
            throw new IllegalArgumentException("no band below " + PhotoEvaluator.MIN_AGE + ", got " + age);
        }
        if (age >= LAST_START) {
            return LAST_START + "+";
        }
        int start = PhotoEvaluator.MIN_AGE + (age - PhotoEvaluator.MIN_AGE) / WIDTH * WIDTH;
        return start + "-" + (start + WIDTH - 1);
    }
}
