package app.ageguessr.features.hints.ImportHints;

import java.util.List;
import java.util.Map;

/**
 * Outcome of an import, logged at the end. {@code refusedLines} names each refused line
 * by its key, or by its position when it has none.
 */
record ImportHintsReport(
        int read, int added, int alreadyImported, Map<HintRejection, Integer> refused, List<String> refusedLines) {}
