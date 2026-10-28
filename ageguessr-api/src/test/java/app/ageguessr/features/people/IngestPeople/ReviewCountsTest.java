package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.ReviewStatus;
import org.junit.jupiter.api.Test;

class ReviewCountsTest {

    @Test
    void countsAPendingPhotoAtTheRateAndAnApprovedOneInFull() {
        ApprovalRate rate = new ApprovalRate(0.4, 0, false);

        assertThat(new ReviewCounts(10, 5, 100).expected(rate)).isEqualTo(12.0);
        assertThat(new ReviewCounts(10, 5, 100).inPlay()).isEqualTo(15);
    }

    @Test
    void buildsCountsFromOneStatusAndAddsThem() {
        ReviewCounts sum = ReviewCounts.of(ReviewStatus.APPROVED, 3)
                .plus(ReviewCounts.of(ReviewStatus.PENDING, 4))
                .plus(ReviewCounts.of(ReviewStatus.REJECTED, 5));

        assertThat(sum).isEqualTo(new ReviewCounts(3, 4, 5));
    }
}
