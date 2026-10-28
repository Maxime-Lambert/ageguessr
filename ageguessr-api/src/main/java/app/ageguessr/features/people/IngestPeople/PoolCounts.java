package app.ageguessr.features.people.IngestPeople;

import java.util.Map;

/**
 * The stored photos of people not excluded, per balance cell and per age band, each split
 * by review status. {@code byCell} leaves out people whose cell is unknown; {@code byAgeBand}
 * counts every photo, so its total is the whole pool.
 */
record PoolCounts(Map<Cell, ReviewCounts> byCell, Map<String, ReviewCounts> byAgeBand) {

    static final PoolCounts EMPTY = new PoolCounts(Map.of(), Map.of());

    PoolCounts {
        byCell = Map.copyOf(byCell);
        byAgeBand = Map.copyOf(byAgeBand);
    }

    ReviewCounts cell(Cell cell) {
        return byCell.getOrDefault(cell, ReviewCounts.NONE);
    }

    ReviewCounts ageBand(String band) {
        return byAgeBand.getOrDefault(band, ReviewCounts.NONE);
    }

    ReviewCounts total() {
        return byAgeBand.values().stream().reduce(ReviewCounts.NONE, ReviewCounts::plus);
    }
}
