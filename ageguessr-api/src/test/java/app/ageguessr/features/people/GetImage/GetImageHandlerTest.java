package app.ageguessr.features.people.GetImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import app.ageguessr.features.people.PhotoImage;
import app.ageguessr.features.people.PhotoImageRepository;
import app.ageguessr.shared.exceptions.NotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetImageHandlerTest {

    private final PhotoImageRepository images = mock(PhotoImageRepository.class);
    private final GetImageHandler handler = new GetImageHandler(images);

    @Test
    void returnsTheBytesAndTypeOfAServableImage() {
        UUID publicId = UUID.randomUUID();
        when(images.findServableByPublicId(publicId))
                .thenReturn(Optional.of(PhotoImage.builder().content(new byte[] {1, 2}).contentType("image/jpeg").build()));

        ImageResponse response = handler.handle(new GetImageQuery(publicId.toString()));

        assertThat(response.content()).containsExactly(1, 2);
        assertThat(response.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void anUnknownOrUnservableIdIsNotFound() {
        when(images.findServableByPublicId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.handle(new GetImageQuery(UUID.randomUUID().toString())))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Image not found");
    }

    @Test
    void aMalformedIdIsTheSameNotFoundWithoutQueryingTheDatabase() {
        assertThatThrownBy(() -> handler.handle(new GetImageQuery("Portrait_of_Jane_Doe.jpg")))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Image not found");
        verifyNoInteractions(images);
    }
}
