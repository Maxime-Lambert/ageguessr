package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageReencoderTest {

    private static final String SECRET = "Jane Doe at the 1976 premiere";

    private final ImageReencoder reencoder = new ImageReencoder();

    @Test
    void keepsTheDimensionsAndWritesAJpeg() throws IOException {
        ImageReencoder.Reencoded result = reencoder.reencode(jpeg(120, 80));

        assertThat(result.width()).isEqualTo(120);
        assertThat(result.height()).isEqualTo(80);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(result.content()));
        assertThat(decoded.getWidth()).isEqualTo(120);
        assertThat(result.content()).startsWith((byte) 0xFF, (byte) 0xD8);
    }

    @Test
    void dropsExifAndCommentsThatNameThePerson() throws IOException {
        byte[] withMetadata = withSegments(jpeg(64, 64));
        assertThat(contains(withMetadata, SECRET)).isTrue();

        byte[] result = reencoder.reencode(withMetadata).content();

        assertThat(contains(result, SECRET)).isFalse();
        assertThat(contains(result, "Exif")).isFalse();
        assertThat(hasMarker(result, 0xE1)).as("APP1 (EXIF/XMP) segment").isFalse();
        assertThat(hasMarker(result, 0xFE)).as("COM segment").isFalse();
    }

    @Test
    void changesTheBytesSoTheFileCannotBeFoundByItsHash() throws IOException {
        byte[] original = jpeg(64, 64);

        assertThat(reencoder.reencode(original).content()).isNotEqualTo(original);
    }

    @Test
    void convertsAnImageWithTransparencyToPlainRgb() throws IOException {
        BufferedImage argb = new BufferedImage(40, 30, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(argb, "png", png);

        ImageReencoder.Reencoded result = reencoder.reencode(png.toByteArray());

        assertThat(ImageIO.read(new ByteArrayInputStream(result.content())).getColorModel().hasAlpha()).isFalse();
    }

    @Test
    void refusesAnImageOverThePixelLimitBeforeDecodingIt() throws IOException {
        ImageReencoder small = new ImageReencoder(100 * 100);

        assertThatThrownBy(() -> small.reencode(jpeg(101, 100)))
                .isInstanceOf(ImageRejectedException.class)
                .extracting(e -> ((ImageRejectedException) e).reason())
                .isEqualTo(ImageRejectedException.Reason.TOO_LARGE);
    }

    @Test
    void acceptsAnImageExactlyAtThePixelLimit() throws IOException {
        assertThat(new ImageReencoder(100 * 100).reencode(jpeg(100, 100)).width()).isEqualTo(100);
    }

    @Test
    void refusesBytesThatAreNotAnImage() {
        assertThatThrownBy(() -> reencoder.reencode("<html>not found</html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ImageRejectedException.class)
                .extracting(e -> ((ImageRejectedException) e).reason())
                .isEqualTo(ImageRejectedException.Reason.UNREADABLE);
    }

    @Test
    void refusesATruncatedJpeg() throws IOException {
        byte[] full = jpeg(64, 64);
        byte[] truncated = java.util.Arrays.copyOf(full, 40);

        assertThatThrownBy(() -> reencoder.reencode(truncated)).isInstanceOf(ImageRejectedException.class);
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, (x * 7 + y * 13) & 0xFFFFFF);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }

    /** Inserts an EXIF APP1 segment and a COM segment carrying {@link #SECRET} right after the SOI marker. */
    private static byte[] withSegments(byte[] jpeg) {
        byte[] exif = segment(0xE1, ("Exif\0\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1));
        byte[] comment = segment(0xFE, SECRET.getBytes(StandardCharsets.ISO_8859_1));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.writeBytes(exif);
        out.writeBytes(comment);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static byte[] segment(int marker, byte[] payload) {
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(marker);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    /** Looks for a marker among the header segments, before the image data starts (SOS, 0xDA). */
    private static boolean hasMarker(byte[] jpeg, int marker) {
        int i = 2;
        while (i + 3 < jpeg.length && (jpeg[i] & 0xFF) == 0xFF) {
            int current = jpeg[i + 1] & 0xFF;
            if (current == marker) {
                return true;
            }
            if (current == 0xDA) {
                return false;
            }
            i += 2 + (((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF));
        }
        return false;
    }
}
