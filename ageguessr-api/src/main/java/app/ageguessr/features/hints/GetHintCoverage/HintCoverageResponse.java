package app.ageguessr.features.hints.GetHintCoverage;

import app.ageguessr.features.hints.HintCategory;
import java.util.List;

/**
 * Approved hints per year, category and audience, for the years the game needs today.
 * {@code gaps} counts the cells under the minimum, each audience apart: that category is
 * hidden for that year and audience.
 */
public record HintCoverageResponse(int minimum, long pending, int gaps, List<Row> rows) {

    public record Row(int year, List<Cell> cells) {}

    public record Cell(HintCategory category, int fr, int en) {}
}
