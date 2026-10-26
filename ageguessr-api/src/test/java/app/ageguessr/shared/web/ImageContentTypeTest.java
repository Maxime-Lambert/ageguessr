package app.ageguessr.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class ImageContentTypeTest {

    @Test
    void keepsAnImageType() {
        assertThat(ImageContentType.of("image/jpeg")).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(ImageContentType.of("image/png")).isEqualTo(MediaType.IMAGE_PNG);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"text/html", "application/javascript", "image", "not a type", "IMAGEjpeg"})
    void servesAnythingElseAsOpaqueBytes(String stored) {
        assertThat(ImageContentType.of(stored)).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    }
}
