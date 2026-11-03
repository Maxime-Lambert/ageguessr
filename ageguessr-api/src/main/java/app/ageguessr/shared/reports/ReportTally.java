package app.ageguessr.shared.reports;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The open reports of one target (a photo, a hint), all reasons together: what a line of
 * an admin queue shows. Built from the per-reason counts of the database.
 *
 * @param reasons how many open reports for each reason given at least once
 * @param firstAt the oldest open report, the tie-breaker of the queue
 */
public record ReportTally<R extends Enum<R>>(UUID targetId, Map<R, Long> reasons, long total, Instant firstAt) {

    /** One per-reason count, as the database groups them. */
    public record Count<R extends Enum<R>>(UUID targetId, R reason, long count, Instant firstAt) {}

    /** The queue order: the most reported first, then the oldest first. */
    public static <R extends Enum<R>> List<ReportTally<R>> of(Class<R> reasonType, List<Count<R>> counts) {
        Map<UUID, List<Count<R>>> byTarget = new LinkedHashMap<>();
        counts.forEach(count -> byTarget.computeIfAbsent(count.targetId(), id -> new ArrayList<>()).add(count));
        return byTarget.entrySet().stream()
                .map(entry -> tally(reasonType, entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong((ReportTally<R> tally) -> tally.total())
                        .reversed()
                        .thenComparing(ReportTally::firstAt))
                .toList();
    }

    private static <R extends Enum<R>> ReportTally<R> tally(Class<R> reasonType, UUID targetId, List<Count<R>> counts) {
        Map<R, Long> reasons = new EnumMap<>(reasonType);
        counts.forEach(count -> reasons.put(count.reason(), count.count()));
        long total = counts.stream().mapToLong(Count::count).sum();
        Instant firstAt = counts.stream().map(Count::firstAt).min(Comparator.naturalOrder()).orElseThrow();
        return new ReportTally<>(targetId, reasons, total, firstAt);
    }
}
