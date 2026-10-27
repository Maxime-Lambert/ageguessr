package app.ageguessr.features.people.GetPhotoForReview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import app.ageguessr.features.people.PhotoImage;
import app.ageguessr.features.people.PhotoImageRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetPhotoForReviewHandlerTest {

    private final PhotoImageRepository images = mock(PhotoImageRepository.class);
    private final GetPhotoForReviewHandler handler = new GetPhotoForReviewHandler(images);

    @Test
    void returnsTheImageWhateverItsReviewStatus() {
        UUID id = UUID.randomUUID();
        PhotoImage image = PhotoImage.builder().photoId(id).content(new byte[] {1}).contentType("image/jpeg").build();
        when(images.findById(id)).thenReturn(Optional.of(image));

        ReviewImageResponse response = handler.handle(new GetPhotoForReviewQuery(id));

        assertThat(response.content()).containsExactly(1);
        assertThat(response.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void anUnknownPhotoIsNotFound() {
        when(images.findById(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetPhotoForReviewQuery(UUID.randomUUID())))
                .isInstanceOf(NotFoundException.class);
    }
}
