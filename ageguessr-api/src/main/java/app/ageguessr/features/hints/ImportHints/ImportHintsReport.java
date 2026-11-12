package app.ageguessr.features.hints.ImportHints;

import java.util.List;
import java.util.Map;

/**
 * Outcome of an import, logged at the end. {@code refusedLines} names each refused line
 * by its key, or by its position when it has none. {@code named}: hints already imported
 * that got their subject (V24). {@code retired} and {@code reactivated} stay at zero
 * without the retire-absent option.
 */
record ImportHintsReport(
        int read,
        int added,
        int alreadyImported,
        int named,
        int retired,
        int reactivated,
        Map<HintRejection, Integer> refused,
        List<String> refusedLines) {}
