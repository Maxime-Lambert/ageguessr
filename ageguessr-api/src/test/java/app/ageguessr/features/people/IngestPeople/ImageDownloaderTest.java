package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ImageDownloaderTest {

    private static final String URL =
            "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ee/A.jpg/800px-A.jpg?utm_source=commons.wikimedia.org";

    private MockRestServiceServer server;
    private ImageDownloader downloader;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        WikimediaHttp http = new WikimediaHttp(builder.build(), Fixtures.MAPPER, d -> {}, Duration.ZERO, 2);
        downloader = new ImageDownloader(http, 10);
    }

    @Test
    void downloadsAThumbnailFromUploadWikimedia() {
        server.expect(requestTo(URL)).andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG));

        assertThat(downloader.download(URL)).containsExactly(1, 2, 3);
    }

    @Test
    void downloadsAThumbnailFromThumbWikimedia() {
        String thumb = "https://thumb.wikimedia.org/wikipedia/commons/thumb/e/eb/A.jpg/960px-A.jpg";
        server.expect(requestTo(thumb)).andRespond(withSuccess(new byte[] {4}, MediaType.IMAGE_JPEG));

        assertThat(downloader.download(thumb)).containsExactly(4);
    }

    @Test
    void acceptsAFileOfExactlyTheMaximumSize() {
        server.expect(requestTo(URL)).andRespond(withSuccess(new byte[10], MediaType.IMAGE_JPEG));

        assertThat(downloader.download(URL)).hasSize(10);
    }

    @Test
    void refusesAFileOverTheMaximumSizeWithoutRetrying() {
        server.expect(requestTo(URL)).andRespond(withSuccess(new byte[11], MediaType.IMAGE_JPEG));

        assertThatThrownBy(() -> downloader.download(URL))
                .isInstanceOf(ImageRejectedException.class)
                .hasMessageContaining("larger than 10 bytes");
        server.verify();
    }

    @Test
    void retriesThrottledThumbnails() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withSuccess(new byte[] {1}, MediaType.IMAGE_JPEG));

        assertThat(downloader.download(URL)).containsExactly(1);
    }

    @Test
    void reportsADownloadThatKeepsFailing() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> downloader.download(URL))
                .isInstanceOf(ImageRejectedException.class)
                .extracting(e -> ((ImageRejectedException) e).reason())
                .isEqualTo(ImageRejectedException.Reason.DOWNLOAD_FAILED);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        "http://upload.wikimedia.org/a.jpg",
        "https://commons.wikimedia.org/a.jpg",
        "https://upload.wikimedia.org.evil.example/a.jpg",
        "https://thumb.wikimedia.org.evil.example/a.jpg",
        "https://wikimedia.org/a.jpg",
        "https://evil.example/upload.wikimedia.org/a.jpg",
        "https://upload.wikimedia.org:8443/a.jpg",
        "https://user@upload.wikimedia.org/a.jpg",
        "https://169.254.169.254/latest/meta-data",
        "file:///etc/passwd",
        "not a url at all"
    })
    void refusesAnyOtherHostBeforeSendingAnything(String url) {
        assertThatThrownBy(() -> downloader.download(url))
                .isInstanceOf(ImageRejectedException.class)
                .extracting(e -> ((ImageRejectedException) e).reason())
                .isEqualTo(ImageRejectedException.Reason.HOST_NOT_ALLOWED);
        server.verify();
    }

    @Test
    void theHostCheckIgnoresCase() {
        assertThat(ImageDownloader.allowed("https://UPLOAD.Wikimedia.org/a.jpg").getHost())
                .isEqualToIgnoringCase("upload.wikimedia.org");
    }
}
