package app.ageguessr.features.game;

/**
 * How far a wrong first guess is, told with its {@link Direction}: the side and a band
 * together leave a single span of 5 or 10 years, which the hints help to narrow down
 * (docs/decisions/architecture.md, « Déroulé d'une manche »).
 */
public enum GapBand {
    /** 1 to 5 years off. */
    SLIGHTLY(1, 5),
    /** 6 to 15 years off. */
    CLEARLY(6, 15),
    /** 16 years off or more: the span runs to the end of the guess range. */
    FAR(16, Integer.MAX_VALUE);

    private final int minGap;
    private final int maxGap;

    GapBand(int minGap, int maxGap) {
        this.minGap = minGap;
        this.maxGap = maxGap;
    }

    public int minGap() {
        return minGap;
    }

    public int maxGap() {
        return maxGap;
    }

    public static GapBand of(int guess, int age) {
        int gap = Math.abs(age - guess);
        if (gap == 0) {
            throw new IllegalArgumentException("An exact guess has no gap");
        }
        for (GapBand band : values()) {
            if (gap <= band.maxGap) {
                return band;
            }
        }
        throw new IllegalStateException("FAR has no upper bound");
    }
}
