package app.ageguessr.features.people.IngestPeople;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Downloads a Commons thumbnail. The URL comes from the Commons API, but it is still
 * checked against an allow-list before any request: the server must never fetch an
 * arbitrary address on someone else's say-so (SSRF).
 */
@Component
@RequiredArgsConstructor
class ImageDownloader {

    /**
     * Where Commons serves files. Thumbnails moved to thumb.wikimedia.org (seen on a real
     * run, 2026-10-26); originals and some thumbnails still come from upload.wikimedia.org.
     */
    static final Set<String> ALLOWED_HOSTS = Set.of("upload.wikimedia.org", "thumb.wikimedia.org");

    private final WikimediaHttp http;
    private final IngestProperties properties;

    /** @throws ImageRejectedException for a URL outside the allow-list or a file over the size cap */
    byte[] download(String url) {
        URI uri = allowed(url);
        try {
            return http.getBytes(uri, properties.maxImageBytes());
        } catch (WikimediaUnavailableException e) {
            throw new ImageRejectedException(ImageRejectedException.Reason.DOWNLOAD_FAILED, e.getMessage());
        }
    }

    static URI allowed(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException | NullPointerException e) {
            throw new ImageRejectedException(ImageRejectedException.Reason.HOST_NOT_ALLOWED, "not a URI: " + url);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !ALLOWED_HOSTS.contains(host) || uri.getPort() != -1
                || uri.getUserInfo() != null) {
            throw new ImageRejectedException(ImageRejectedException.Reason.HOST_NOT_ALLOWED, "refused URL: " + url);
        }
        return uri;
    }
}
