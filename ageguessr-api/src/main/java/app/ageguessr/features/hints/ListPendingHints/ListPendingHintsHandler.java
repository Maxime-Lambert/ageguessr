package app.ageguessr.features.hints.ListPendingHints;

import app.ageguessr.features.hints.Hint;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListPendingHintsHandler {

    private final HintRepository hints;

    @Transactional(readOnly = true)
    public PendingHintsResponse handle(ListPendingHintsQuery query) {
        List<PendingHintsResponse.Item> items = hints
                .findByReviewStatusOrderByCreatedAtAscIdAsc(
                        HintReviewStatus.PENDING, PageRequest.ofSize(query.limit()))
                .stream()
                .map(ListPendingHintsHandler::item)
                .toList();
        return new PendingHintsResponse(hints.countByReviewStatus(HintReviewStatus.PENDING), items);
    }

    private static PendingHintsResponse.Item item(Hint hint) {
        return new PendingHintsResponse.Item(
                hint.getId(),
                hint.getCategory(),
                hint.getYear(),
                hint.getAudience(),
                hint.getTextEn(),
                hint.getTextFr(),
                hint.getSourceUrl(),
                hint.getSourceKind());
    }
}
