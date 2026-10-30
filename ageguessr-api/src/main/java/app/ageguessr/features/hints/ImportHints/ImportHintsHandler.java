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
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds the hints whose key is not in the database yet. A hint already imported is never
 * touched: it may carry a curation decision or a text corrected by hand. A bad line is
 * refused and reported, the others still go in.
 */
@Service
@RequiredArgsConstructor
class ImportHintsHandler {

    private final HintRepository hints;
    private final Clock clock;

    @Transactional
    public ImportHintsReport handle(List<HintFileEntry> entries) {
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
        return new ImportHintsReport(entries.size(), toAdd.size(), alreadyImported, refused, refusedLines);
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
