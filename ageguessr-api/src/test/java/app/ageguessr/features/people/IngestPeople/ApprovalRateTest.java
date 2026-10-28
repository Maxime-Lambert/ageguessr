package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.ReviewStatus;
import org.junit.jupiter.api.Test;

class ApprovalRateTest {

    @Test
    void assumesTheConfiguredRateUntilEnoughPhotosAreReviewed() {
        ApprovalRate rate = ApprovalRate.of(new ReviewCounts(200, 5000, 99), 0.4);

        assertThat(rate).isEqualTo(new ApprovalRate(0.4, 299, false));
        assertThat(rate.toString()).startsWith("0.40 assumed");
    }

    @Test
    void observesTheRateFromTheReviewedPhotos() {
        ApprovalRate rate = ApprovalRate.of(new ReviewCounts(120, 5000, 180), 0.4);

        assertThat(rate).isEqualTo(new ApprovalRate(0.4, 300, true));
        assertThat(ApprovalRate.of(new ReviewCounts(150, 0, 150), 0.4).value()).isEqualTo(0.5);
        assertThat(rate.toString()).isEqualTo("0.40 observed on 300 reviewed photos");
    }

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
