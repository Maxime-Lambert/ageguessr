package app.ageguessr.features.hints.ImportHints;

import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds the hints whose key is not in the database yet. A hint already imported is never
 * touched: it may carry a curation decision or a text corrected by hand. A bad line is
 * refused and reported, the others still go in.
 *
 * <p>With {@code retireAbsent}, the files become the whole set of hints offered to a round:
 * a key absent from them is retired, a retired key back in them is offered again (V22).
 * Nothing is deleted. The same transaction as the additions, so that no category is left
 * empty between the two.
 */
@Service
@RequiredArgsConstructor
class ImportHintsHandler {

    /** Keys per update: well under the bind parameter limit of Postgres (65 535). */
    static final int RETIRE_BATCH = 1_000;

    private final HintRepository hints;
    private final Clock clock;

    @Transactional
    public ImportHintsReport handle(List<HintFileEntry> entries) {
        return handle(entries, false);
    }

    @Transactional
    public ImportHintsReport handle(List<HintFileEntry> entries, boolean retireAbsent) {
        Instant now = clock.instant();
        int currentYear = Year.now(clock).getValue();
        Set<String> existing = hints.findAllDedupeKeys();
        Set<String> seen = new HashSet<>();
        List<Hint> toAdd = new ArrayList<>();
        Map<HintRejection, Integer> refused = new EnumMap<>(HintRejection.class);
        List<String> refusedLines = new ArrayList<>();
        int alreadyImported = 0;

        for (int i = 0; i < entries.size(); i++) {
            HintFileEntry entry = entries.get(i);
            Optional<HintRejection> rejection = HintRules.check(entry, currentYear);
            if (rejection.isEmpty() && !seen.add(entry.dedupeKey())) {
                rejection = Optional.of(HintRejection.DUPLICATE_KEY);
            }
            if (rejection.isPresent()) {
                refused.merge(rejection.get(), 1, Integer::sum);
                refusedLines.add(label(entry, i) + ": " + rejection.get());
            } else if (existing.contains(entry.dedupeKey())) {
                alreadyImported++;
            } else {
                toAdd.add(toHint(entry, now));
            }
        }
        hints.saveAll(toAdd);
        int retired = 0;
        int reactivated = 0;
        if (retireAbsent) {
            // A refused line keeps its hint: a bad line in the files must not retire a good hint.
            Set<String> inFiles = entries.stream()
                    .map(HintFileEntry::dedupeKey)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Set<String> retiredBefore = hints.findRetiredDedupeKeys();
            List<String> toRetire = existing.stream()
                    .filter(key -> !inFiles.contains(key) && !retiredBefore.contains(key))
                    .sorted()
                    .toList();
            List<String> toReactivate =
                    retiredBefore.stream().filter(inFiles::contains).sorted().toList();
            retired = setRetiredAt(toRetire, now);
            reactivated = setRetiredAt(toReactivate, null);
        }
        return new ImportHintsReport(
                entries.size(), toAdd.size(), alreadyImported, retired, reactivated, refused, refusedLines);
    }

    private int setRetiredAt(List<String> keys, Instant retiredAt) {
        int updated = 0;
        for (int start = 0; start < keys.size(); start += RETIRE_BATCH) {
            updated += hints.setRetiredAt(keys.subList(start, Math.min(keys.size(), start + RETIRE_BATCH)), retiredAt);
        }
        return updated;
    }

    private static String label(HintFileEntry entry, int index) {
        return entry.dedupeKey() != null ? entry.dedupeKey() : "line " + (index + 1);
    }

    private static Hint toHint(HintFileEntry entry, Instant now) {
        return Hint.builder()
                .category(HintCategory.valueOf(entry.category()))
                .year(entry.year())
                .audience(HintAudience.valueOf(entry.audience()))
                .textEn(entry.textEn())
                .textFr(entry.textFr())
                .sourceUrl(entry.sourceUrl())
                .sourceKind(entry.sourceKind())
                .subjectQid(entry.subjectQid())
                .dedupeKey(entry.dedupeKey())
                .reviewStatus(entry.translated() ? HintReviewStatus.PENDING : HintReviewStatus.APPROVED)
                .createdAt(now)
                .build();
    }
}
