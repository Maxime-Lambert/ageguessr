package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

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
    void keepsTheAssumedRateWhenNoReviewedPhotoWasApproved() {
        assertThat(ApprovalRate.of(new ReviewCounts(0, 10, 400), 0.4)).isEqualTo(new ApprovalRate(0.4, 400, false));
    }

    @Test
    void observesARateOfOneWhenEveryReviewedPhotoIsApproved() {
        assertThat(ApprovalRate.of(new ReviewCounts(300, 10, 0), 0.4)).isEqualTo(new ApprovalRate(1.0, 300, true));
    }
}
