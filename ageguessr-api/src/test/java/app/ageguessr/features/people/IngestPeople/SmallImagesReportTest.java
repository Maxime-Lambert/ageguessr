package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;

import app.ageguessr.features.people.PersonPhotoRepository;
import app.ageguessr.features.people.ReviewStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SmallImagesReportTest {

    @Test
    void printsTheCounts() {
        assertThat(new SmallImagesReport(10, 2, 8, 1))
                .hasToString("small images found=10, enlarged=2, rejected=8 (of which approved=1)");
    }

    @Test
    void neverPrintsTheFileNameOfASmallImage() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertThat(new PersonPhotoRepository.SmallImage(id, "Jane Doe 1990.jpg", ReviewStatus.PENDING))
                .hasToString("SmallImage[" + id + ", PENDING]");
    }
}
