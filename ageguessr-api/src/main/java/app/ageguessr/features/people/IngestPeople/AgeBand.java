package app.ageguessr.features.people.IngestPeople;

import java.util.ArrayList;
import java.util.List;

/**
 * Bands of 8 years of age on a photo, from 18: "18-25", "26-33"... and "82+" for the
 * rest (people born since 1930 are at most 96 on a photo). Used to see how the pool
 * spreads across ages.
 */
final class AgeBand {

    static final int WIDTH = 8;
    static final int LAST_START = 82;

    /** Every band, youngest first. */
    static final List<String> ALL = all();

    private AgeBand() {}

    private static List<String> all() {
        List<String> bands = new ArrayList<>();
        for (int start = PhotoEvaluator.MIN_AGE; start < LAST_START; start += WIDTH) {
            bands.add(start + "-" + (start + WIDTH - 1));
        }
        bands.add(LAST_START + "+");
        return List.copyOf(bands);
    }

    /** The youngest age of a band: 74 for "74-81", 82 for "82+". */
    static int start(String band) {
        if (!ALL.contains(band)) {
            throw new IllegalArgumentException("unknown age band " + band + ", expected one of " + ALL);
        }
        return Integer.parseInt(band.split("[-+]")[0]);
    }

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
