package app.ageguessr.features.people.IngestPeople;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.stereotype.Component;

/**
 * Decodes a downloaded image to pixels and writes a brand-new JPEG from them. Only the
 * pixels survive: EXIF, XMP, IPTC and comments (which often hold the person's name, a
 * caption or the date) are never copied, because the new file is written without any
 * metadata. Re-encoding also changes every byte, so the stored image cannot be looked up
 * on Commons by its hash.
 */
@Component
class ImageReencoder {

    static final String CONTENT_TYPE = "image/jpeg";
    static final float QUALITY = 0.85f;
    /** 800 px wide thumbnails stay far below this; anything above is suspicious (decompression bomb). */
    static final long DEFAULT_MAX_PIXELS = 20_000_000L;

    record Reencoded(byte[] content, int width, int height) {}

    private final long maxPixels;

    ImageReencoder() {
        this(DEFAULT_MAX_PIXELS);
    }

    ImageReencoder(long maxPixels) {
        this.maxPixels = maxPixels;
    }

    Reencoded reencode(byte[] original) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw unreadable("no decoder for this format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                // Dimensions come from the header: checked before decoding, so a tiny file
                // claiming 100000 x 100000 pixels never gets a buffer allocated.
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > maxPixels) {
                    throw new ImageRejectedException(
                            ImageRejectedException.Reason.TOO_LARGE, pixels + " pixels, over " + maxPixels);
                }
                BufferedImage decoded = reader.read(0);
                return write(toRgb(decoded));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ImageRejectedException rejected) {
                throw rejected;
            }
            throw unreadable(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** JPEG has no alpha channel and ImageIO cannot write CMYK: draw everything onto plain RGB. */
    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    private static Reencoded write(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(output);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(QUALITY);
            // null metadata: the writer emits a bare JFIF header and nothing else.
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return new Reencoded(out.toByteArray(), image.getWidth(), image.getHeight());
    }

    private static ImageRejectedException unreadable(String message) {
        return new ImageRejectedException(ImageRejectedException.Reason.UNREADABLE, message);
    }
}
