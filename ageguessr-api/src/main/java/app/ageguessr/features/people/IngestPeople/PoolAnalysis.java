package app.ageguessr.features.people.IngestPeople;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Where the pool stands against its target, in approved photos to expect, and what the
 * next waves should collect (--analyze). Orders of magnitude, not exact counts: the
 * approval rate is an average, so each cell and band only gets close to its share.
 *
 * <p>Cells fill themselves: their quotas already count pending photos at the approval
 * rate. Ages need a focused wave: an age band is lacking when it holds less than half of
 * what the median band holds, and the wave lifts it to the median. Only the next wave
 * is suggested: the cell wave moves the median, so the age wave is measured after it
 * (pending photos count at the rate, no need to curate in between).
 */
final class PoolAnalysis {

    static final double LACKING_SHARE = 0.5;
    private static final int ROUNDING = 100;

    record CellLine(Cell cell, ReviewCounts counts, double expected, int quota, int toCollect) {}

    record BandLine(String band, ReviewCounts counts, double expected, boolean lacking) {}

    private final int target;
    private final ApprovalRate rate;
    private final ReviewCounts total;
    private final List<CellLine> cells;
    private final List<BandLine> bands;
    private final double medianBand;

    private PoolAnalysis(
            int target, ApprovalRate rate, ReviewCounts total, List<CellLine> cells, List<BandLine> bands,
            double medianBand) {
        this.target = target;
        this.rate = rate;
        this.total = total;
        this.cells = cells;
        this.bands = bands;
        this.medianBand = medianBand;
    }

    static PoolAnalysis of(PoolCounts counts, ApprovalRate rate, int target) {
        List<CellLine> cells = Cell.ALL.stream()
                .map(cell -> {
                    ReviewCounts c = counts.cell(cell);
                    double expected = c.expected(rate);
                    int quota = cell.quota(target);
                    return new CellLine(cell, c, expected, quota, toCollect(quota - expected, rate));
                })
                .toList();
        List<Double> expectedByBand = AgeBand.ALL.stream()
                .map(band -> counts.ageBand(band).expected(rate))
                .sorted()
                .toList();
        double median = expectedByBand.get(expectedByBand.size() / 2);
        List<BandLine> bands = AgeBand.ALL.stream()
                .map(band -> {
                    double expected = counts.ageBand(band).expected(rate);
                    return new BandLine(band, counts.ageBand(band), expected, expected < median * LACKING_SHARE);
                })
                .toList();
        return new PoolAnalysis(target, rate, counts.total(), cells, bands, median);
    }

    /** Raw photos to collect for {@code missing} approved ones; 0 when nothing is missing. */
    private static int toCollect(double missing, ApprovalRate rate) {
        // Never 0: see ApprovalRate.
        return missing <= 0 ? 0 : (int) Math.ceil(missing / rate.value());
    }

    List<CellLine> cells() {
        return cells;
    }

    List<BandLine> bands() {
        return bands;
    }

    /** Raw photos the cells still need, all cells together. */
    int photosToCollectForCells() {
        return cells.stream().mapToInt(CellLine::toCollect).sum();
    }

    List<String> lackingBands() {
        return bands.stream().filter(BandLine::lacking).map(BandLine::band).toList();
    }

    /** Approved photos the lacking bands miss to reach the median band. */
    int approvedMissingInLackingBands() {
        return (int) Math.ceil(bands.stream()
                .filter(BandLine::lacking)
                .mapToDouble(b -> medianBand - b.expected())
                .sum());
    }

    /**
     * The target of the age wave: room for the missing old photos on top of the current
     * target, rounded up to a hundred since the shares are approximate anyway.
     */
    int ageWaveTarget() {
        return Math.ceilDiv(target + approvedMissingInLackingBands(), ROUNDING) * ROUNDING;
    }

    /** The cell wave while cells miss photos, then the age wave; empty when nothing is missing. */
    Optional<String> nextWave() {
        if (photosToCollectForCells() > 0) {
            return Optional.of(
                    "--limit=" + target + "   (fills the cells, ~" + photosToCollectForCells() + " photos to collect)");
        }
        if (!lackingBands().isEmpty()) {
            return Optional.of("--limit=" + ageWaveTarget() + " --age-bands=" + String.join(",", lackingBands())
                    + "   (lifts the lacking ages toward the median band, ~"
                    + toCollect(approvedMissingInLackingBands(), rate) + " photos to collect)");
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        text.append("Pool analysis for a target of ").append(target).append(" approved photos, approval rate ")
                .append(rate).append('\n');
        text.append(String.format(
                Locale.ROOT,
                "Expected approved: %.0f (approved %d, pending %d, rejected %d)%n",
                total.expected(rate), total.approved(), total.pending(), total.rejected()));
        text.append("Cells: approved/pending/rejected -> expected approved / quota, photos to collect\n");
        for (CellLine line : cells) {
            text.append(String.format(
                    Locale.ROOT,
                    "  %-22s %5d/%5d/%5d -> %6.0f / %4d, %d%n",
                    line.cell(), line.counts().approved(), line.counts().pending(), line.counts().rejected(),
                    line.expected(), line.quota(), line.toCollect()));
        }
        text.append(String.format(
                Locale.ROOT, "Age bands: approved/pending/rejected -> expected approved (median band %.0f)%n", medianBand));
        for (BandLine line : bands) {
            text.append(String.format(
                    Locale.ROOT,
                    "  %-6s %5d/%5d/%5d -> %6.0f%s%n",
                    line.band(), line.counts().approved(), line.counts().pending(), line.counts().rejected(),
                    line.expected(), line.lacking() ? "   lacking" : ""));
        }
        nextWave().ifPresentOrElse(
                wave -> text.append("Next wave (add the exclusions, e.g. --exclude-occupations=Q937857):\n  ")
                        .append(wave)
                        .append("\nThen run --analyze again: pending photos count at the approval rate, no need to "
                                + "curate in between."),
                () -> text.append("Nothing to collect: every cell is at its quota and no age band is lacking."));
        return text.toString();
    }
}
