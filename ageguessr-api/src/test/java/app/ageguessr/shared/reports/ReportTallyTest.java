package app.ageguessr.shared.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReportTallyTest {

    private enum Reason {
        SMALL,
        WRONG
    }

    private static final Instant EARLY = Instant.parse("2026-11-01T08:00:00Z");
    private static final Instant LATE = Instant.parse("2026-11-02T08:00:00Z");

    @Test
    void addsUpTheReasonsOfATargetAndSortsTheMostReportedFirstThenTheOldest() {
        UUID once = UUID.randomUUID();
        UUID thrice = UUID.randomUUID();
        UUID onceEarlier = UUID.randomUUID();

        List<ReportTally<Reason>> tallies = ReportTally.of(Reason.class, List.of(
                new ReportTally.Count<>(once, Reason.SMALL, 1, LATE),
                new ReportTally.Count<>(thrice, Reason.SMALL, 1, LATE),
                new ReportTally.Count<>(thrice, Reason.WRONG, 2, EARLY),
                new ReportTally.Count<>(onceEarlier, Reason.WRONG, 1, EARLY)));

        assertThat(tallies).extracting(ReportTally::targetId).containsExactly(thrice, onceEarlier, once);
        ReportTally<Reason> first = tallies.getFirst();
        assertThat(first.total()).isEqualTo(3);
        assertThat(first.reasons()).isEqualTo(Map.of(Reason.SMALL, 1L, Reason.WRONG, 2L));
        assertThat(first.firstAt()).isEqualTo(EARLY);
    }

    @Test
    void anEmptyQueueStaysEmpty() {
        assertThat(ReportTally.of(Reason.class, List.of())).isEmpty();
    }
}
