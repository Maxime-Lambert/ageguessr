package app.ageguessr.features.hints.GetHintCoverage;

import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintCount;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import java.time.Clock;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coverage of the years the game needs: from the oldest photo of the pool to the current
 * year minus 18, the youngest age on a photo. Cells at zero are kept, an empty category
 * being precisely what this view must show.
 */
@Service
@RequiredArgsConstructor
public class GetHintCoverageHandler {

    /** Minimum of hints per year, category and audience; under it the category is hidden. */
    static final int MINIMUM = 5;

    /** 2026 minus the highest age on a photo of the pool (plans/active-plan.md). */
    static final int FIRST_YEAR = 1932;

    static final int YOUNGEST_AGE_ON_A_PHOTO = 18;

    private final HintRepository hints;
    private final Clock clock;

    @Transactional(readOnly = true)
    public HintCoverageResponse handle() {
        List<HintCount> counts = hints.countApproved();
        int lastYear = Year.now(clock).getValue() - YOUNGEST_AGE_ON_A_PHOTO;
        List<HintCoverageResponse.Row> rows = IntStream.rangeClosed(FIRST_YEAR, lastYear)
                .mapToObj(year -> row(year, counts))
                .toList();
        int gaps = (int) rows.stream()
                .flatMap(row -> row.cells().stream())
                .flatMapToInt(cell -> IntStream.of(cell.fr(), cell.en()))
                .filter(total -> total < MINIMUM)
                .count();
        return new HintCoverageResponse(MINIMUM, hints.countByReviewStatus(HintReviewStatus.PENDING), gaps, rows);
    }

    private static HintCoverageResponse.Row row(int year, List<HintCount> counts) {
        List<HintCoverageResponse.Cell> cells = new ArrayList<>();
        for (HintCategory category : HintCategory.values()) {
            int all = total(counts, year, category, HintAudience.ALL);
            cells.add(new HintCoverageResponse.Cell(
                    category,
                    all + total(counts, year, category, HintAudience.FR),
                    all + total(counts, year, category, HintAudience.EN)));
        }
        return new HintCoverageResponse.Row(year, cells);
    }

    private static int total(List<HintCount> counts, int year, HintCategory category, HintAudience audience) {
        return counts.stream()
                .filter(c -> c.year() == year && c.category() == category && c.audience() == audience)
                .mapToInt(c -> (int) c.total())
                .sum();
    }
}
