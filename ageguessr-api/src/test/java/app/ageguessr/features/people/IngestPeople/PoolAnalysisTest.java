package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PoolAnalysisTest {

    private static final ApprovalRate RATE = new ApprovalRate(0.4, 0, false);
    private static final Cell AFRICA_WOMEN = new Cell(Continent.AFRICA, Gender.FEMALE);

    @Test
    void collectsForEachCellWhatItMissesDividedByTheRate() {
        // Quota 10 for a target of 120. Expected 4 + 5 × 0.4 = 6: 4 missing, 10 to collect.
        PoolCounts counts = new PoolCounts(Map.of(AFRICA_WOMEN, new ReviewCounts(4, 5, 20)), flatBands(10));

        PoolAnalysis analysis = PoolAnalysis.of(counts, RATE, 120);

        assertThat(analysis.cells())
                .filteredOn(c -> c.cell().equals(AFRICA_WOMEN))
                .singleElement()
                .satisfies(line -> {
                    assertThat(line.expected()).isEqualTo(6.0);
                    assertThat(line.quota()).isEqualTo(10);
                    assertThat(line.toCollect()).isEqualTo(10);
                });
    }

    @Test
    void collectsNothingForACellAtOrAboveItsQuota() {
        PoolCounts counts = new PoolCounts(Map.of(AFRICA_WOMEN, new ReviewCounts(12, 0, 0)), flatBands(10));

        PoolAnalysis analysis = PoolAnalysis.of(counts, RATE, 120);

        assertThat(analysis.cells()).filteredOn(c -> c.cell().equals(AFRICA_WOMEN))
                .singleElement()
                .extracting(PoolAnalysis.CellLine::toCollect)
                .isEqualTo(0);
    }

    @Test
    void flagsTheBandsUnderHalfTheMedianBand() {
        Map<String, ReviewCounts> bands = new HashMap<>(flatBands(100));
        bands.put("74-81", new ReviewCounts(40, 0, 0));
        bands.put("82+", new ReviewCounts(10, 0, 0));
        bands.put("66-73", new ReviewCounts(50, 0, 0));

        PoolAnalysis analysis = PoolAnalysis.of(new PoolCounts(Map.of(), bands), RATE, 5000);

        // Median 100: 50 is not under 50, 40 and 10 are.
        assertThat(analysis.lackingBands()).containsExactly("74-81", "82+");
        assertThat(analysis.approvedMissingInLackingBands()).isEqualTo(150);
        // 5000 + 150, rounded up to a hundred.
        assertThat(analysis.ageWaveTarget()).isEqualTo(5200);
    }

    @Test
    void suggestsTheCellWaveThenTheAgeWave() {
        Map<String, ReviewCounts> bands = new HashMap<>(flatBands(100));
        bands.put("82+", new ReviewCounts(10, 0, 0));

        PoolAnalysis analysis = PoolAnalysis.of(new PoolCounts(Map.of(), bands), RATE, 120);

        // 12 cells × quota 10 to collect from nothing: 120 / 0.4 = 300; 82+ misses 90.
        assertThat(analysis.suggestedWaves())
                .containsExactly(
                        "--limit=120   (fills the cells, ~" + analysis.photosToCollectForCells() + " photos to collect)",
                        "--limit=300 --age-bands=82+   (lifts the lacking ages toward the median band, ~225 photos to collect)");
        assertThat(analysis.toString()).contains("82+", "lacking", "Suggested waves");
    }

    @Test
    void saysSoWhenThereIsNothingToCollect() {
        Map<Cell, ReviewCounts> full = new HashMap<>();
        Cell.ALL.forEach(cell -> full.put(cell, new ReviewCounts(10, 0, 0)));

        PoolAnalysis analysis = PoolAnalysis.of(new PoolCounts(full, flatBands(10)), RATE, 120);

        assertThat(analysis.suggestedWaves()).isEmpty();
        assertThat(analysis.toString()).contains("Nothing to collect");
    }

    private static Map<String, ReviewCounts> flatBands(int approved) {
        Map<String, ReviewCounts> bands = new HashMap<>();
        AgeBand.ALL.forEach(band -> bands.put(band, new ReviewCounts(approved, 0, 0)));
        return bands;
    }
}
