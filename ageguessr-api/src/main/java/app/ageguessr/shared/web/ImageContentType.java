package app.ageguessr.shared.web;

import org.springframework.http.MediaType;

/**
 * The Content-Type to serve stored image bytes with. Anything that is not an image is
 * served as opaque bytes: a corrupted content_type such as text/html must never make a
 * browser render the response as a page (stored XSS).
 */
public final class ImageContentType {

    private ImageContentType() {}

    public static MediaType of(String stored) {
        try {
            MediaType type = MediaType.parseMediaType(stored);
            return "image".equals(type.getType()) ? type : MediaType.APPLICATION_OCTET_STREAM;
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
