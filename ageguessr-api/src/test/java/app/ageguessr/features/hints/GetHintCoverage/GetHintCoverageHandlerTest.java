package app.ageguessr.features.hints.GetHintCoverage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.ageguessr.features.hints.HintAudience;
import app.ageguessr.features.hints.HintCategory;
import app.ageguessr.features.hints.HintCount;
import app.ageguessr.features.hints.HintRepository;
import app.ageguessr.features.hints.HintReviewStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetHintCoverageHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");
    private static final int YEARS = 2008 - 1932 + 1;
    private static final int CELLS_PER_YEAR = HintCategory.values().length * 2;

    private final HintRepository hints = mock(HintRepository.class);

    private HintCoverageResponse coverage(HintCount... counts) {
        when(hints.countApproved()).thenReturn(List.of(counts));
        return new GetHintCoverageHandler(hints, Clock.fixed(NOW, ZoneOffset.UTC)).handle();
    }

    private static HintCoverageResponse.Cell cell(HintCoverageResponse response, int year, HintCategory category) {
        return response.rows().stream()
                .filter(row -> row.year() == year)
                .flatMap(row -> row.cells().stream())
                .filter(c -> c.category() == category)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void coversTheOldestPhotoUpToThoseWhoAre18Today() {
        HintCoverageResponse response = coverage();

        assertThat(response.rows().getFirst().year()).isEqualTo(1932);
        assertThat(response.rows().getLast().year()).isEqualTo(2008);
        assertThat(response.rows()).hasSize(YEARS);
    }

    @Test
    void anEmptyCategoryIsAGapForBothAudiences() {
        HintCoverageResponse response = coverage();

        assertThat(cell(response, 1976, HintCategory.SPORT))
                .isEqualTo(new HintCoverageResponse.Cell(HintCategory.SPORT, 0, 0));
        assertThat(response.gaps()).isEqualTo(YEARS * CELLS_PER_YEAR);
    }

    @Test
    void hintsForEveryoneCountForBothAudiences() {
        HintCoverageResponse response = coverage(
                new HintCount(1976, HintCategory.MUSIC, HintAudience.ALL, 3),
                new HintCount(1976, HintCategory.MUSIC, HintAudience.FR, 2),
                new HintCount(1976, HintCategory.MUSIC, HintAudience.EN, 1));

        assertThat(cell(response, 1976, HintCategory.MUSIC))
                .isEqualTo(new HintCoverageResponse.Cell(HintCategory.MUSIC, 5, 4));
    }

    @Test
    void theMinimumIsEnoughAndOneLessIsAGap() {
        HintCoverageResponse response = coverage(
                new HintCount(1976, HintCategory.MUSIC, HintAudience.FR, 5),
                new HintCount(1976, HintCategory.MUSIC, HintAudience.EN, 4));

        assertThat(response.minimum()).isEqualTo(5);
        assertThat(response.gaps()).isEqualTo(YEARS * CELLS_PER_YEAR - 1);
    }

    @Test
    void reportsThePendingHints() {
        when(hints.countByReviewStatus(HintReviewStatus.PENDING)).thenReturn(7L);

        assertThat(coverage().pending()).isEqualTo(7);
    }
}
