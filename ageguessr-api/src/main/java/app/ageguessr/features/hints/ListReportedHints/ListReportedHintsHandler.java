package app.ageguessr.features.hints.ListReportedHints;

import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintReportCount;
import app.ageguessr.features.hints.HintReportReason;
import app.ageguessr.features.hints.HintReportRepository;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.shared.reports.ReportTally;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The queue of reported hints, as ListReportedPhotosHandler for the photos. */
@Service
@RequiredArgsConstructor
public class ListReportedHintsHandler {

    private final HintReportRepository reports;
    private final HintRepository hints;

    @Transactional(readOnly = true)
    public ReportedHintsResponse handle() {
        List<ReportTally<HintReportReason>> tallies = ReportTally.of(
                HintReportReason.class,
                reports.countOpen().stream().map(HintReportCount::asCount).toList());
        Map<UUID, Hint> hintsById = hints.findAllById(tallies.stream().map(ReportTally::targetId).toList())
                .stream()
                .collect(Collectors.toMap(Hint::getId, Function.identity()));
        List<ReportedHintsResponse.Item> items = tallies.stream()
                .map(tally -> item(tally, hintsById.get(tally.targetId())))
                .filter(Objects::nonNull)
                .toList();
        return new ReportedHintsResponse(items);
    }

    /** Null when the hint was deleted between the two reads: its reports go with it (CASCADE). */
    private static ReportedHintsResponse.Item item(ReportTally<HintReportReason> tally, Hint hint) {
        if (hint == null) {
            return null;
        }
        return new ReportedHintsResponse.Item(
                hint.getId(),
                hint.getReviewStatus(),
                hint.getCategory(),
                hint.getYear(),
                hint.getAudience(),
                hint.getTextEn(),
                hint.getTextFr(),
                hint.getSourceUrl(),
                tally.reasons(),
                tally.total(),
                tally.firstAt());
    }
}
