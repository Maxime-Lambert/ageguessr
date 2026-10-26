package app.ageguessr.features.people.IngestPeople;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

class WikimediaHttpTest {

    private static final URI API = URI.create("https://commons.example/w/api.php?action=query");
    private static final Duration PAUSE = Duration.ofMillis(200);

    private MockRestServiceServer server;
    private final List<Duration> waits = new ArrayList<>();
    private WikimediaHttp http;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        http = new WikimediaHttp(builder.build(), Fixtures.MAPPER, waits::add, PAUSE, 3);
    }

    @Test
    void returnsTheJsonBodyAfterAPolitenessPause() {
        server.expect(requestTo(API)).andRespond(withSuccess("{\"ok\": true}", MediaType.APPLICATION_JSON));

        JsonNode body = http.get(API);

        assertThat(body.path("ok").asBoolean()).isTrue();
        assertThat(waits).containsExactly(PAUSE);
        server.verify();
    }

    @Test
    void retriesAMaxlagErrorSentAsHttp200AfterTheServerDelay() {
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.add("Retry-After", "5");
        server.expect(requestTo(API))
                .andRespond(withSuccess(Fixtures.text("commons-maxlag-error.json"), MediaType.APPLICATION_JSON)
                        .headers(retryAfter));
        server.expect(requestTo(API)).andRespond(withSuccess("{\"ok\": true}", MediaType.APPLICATION_JSON));

        JsonNode body = http.get(API);

        assertThat(body.path("ok").asBoolean()).isTrue();
        assertThat(waits).containsExactly(PAUSE, Duration.ofSeconds(5), PAUSE);
    }

    @Test
    void retriesTooManyRequestsAfterTheServerDelay() {
        HttpHeaders retryAfter = new HttpHeaders();
        retryAfter.add("Retry-After", "7");
        server.expect(requestTo(API)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).headers(retryAfter));
        server.expect(requestTo(API)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        http.get(API);

        assertThat(waits).containsExactly(PAUSE, Duration.ofSeconds(7), PAUSE);
    }

    @Test
    void backsOffExponentiallyOnServerErrorsWithoutRetryAfter() {
        server.expect(requestTo(API)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(requestTo(API)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(API)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        http.get(API);

        assertThat(waits).containsExactly(PAUSE, Duration.ofSeconds(2), PAUSE, Duration.ofSeconds(4), PAUSE);
    }

    @Test
    void retriesNetworkErrors() {
        server.expect(requestTo(API)).andRespond(withException(new IOException("connection reset")));
        server.expect(requestTo(API)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        http.get(API);

        server.verify();
    }

    @Test
    void retriesATruncatedJsonBody() {
        server.expect(requestTo(API)).andRespond(withSuccess("{\"query\": {", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        http.get(API);

        server.verify();
    }

    @Test
    void doesNotRetryAClientError() {
        server.expect(times(1), requestTo(API)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> http.get(API))
                .isInstanceOf(WikimediaUnavailableException.class)
                .hasMessageContaining("403");
        server.verify();
    }

    @Test
    void givesUpAfterTheMaximumNumberOfAttemptsWithoutAFinalWait() {
        server.expect(times(3), requestTo(API)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> http.get(API))
                .isInstanceOf(WikimediaUnavailableException.class)
                .hasMessageContaining("3 attempts")
                .hasMessageContaining("503");
        assertThat(waits).containsExactly(PAUSE, Duration.ofSeconds(2), PAUSE, Duration.ofSeconds(4), PAUSE);
        server.verify();
    }

    @Test
    void postsFormsUrlEncoded() {
        URI qlever = URI.create("https://qlever.example/api/wikidata");
        server.expect(requestTo(qlever))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(Map.of("query", "SELECT 1")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("query", "SELECT 1");

        http.postForm(qlever, form);

        server.verify();
    }

    @Test
    void readsBytesUpToTheLimit() {
        server.expect(requestTo(API)).andRespond(withSuccess(new byte[] {1, 2, 3}, MediaType.IMAGE_JPEG));

        assertThat(http.getBytes(API, 3)).containsExactly(1, 2, 3);
    }

    @Test
    void refusesBytesOverTheLimitWithoutRetrying() {
        server.expect(times(1), requestTo(API)).andRespond(withSuccess(new byte[] {1, 2, 3, 4}, MediaType.IMAGE_JPEG));

        assertThatThrownBy(() -> http.getBytes(API, 3))
                .isInstanceOf(WikimediaUnavailableException.class)
                .hasMessageContaining("larger than 3 bytes");
        server.verify();
    }

    @Test
    void stopsWaitingWhenInterrupted() {
        WikimediaHttp interrupted = new WikimediaHttp(
                RestClient.builder().build(), Fixtures.MAPPER, d -> { throw new InterruptedException(); }, PAUSE, 3);

        assertThatThrownBy(() -> interrupted.get(API)).isInstanceOf(WikimediaUnavailableException.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void readsRetryAfterInSecondsAndCapsIt() {
        assertThat(WikimediaHttp.parseRetryAfter("5")).contains(Duration.ofSeconds(5));
        assertThat(WikimediaHttp.parseRetryAfter(" 0 ")).contains(Duration.ZERO);
        assertThat(WikimediaHttp.parseRetryAfter("60")).contains(Duration.ofSeconds(60));
        assertThat(WikimediaHttp.parseRetryAfter("3600")).contains(WikimediaHttp.MAX_BACKOFF);
        assertThat(WikimediaHttp.parseRetryAfter("-1")).isEmpty();
        assertThat(WikimediaHttp.parseRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT")).isEmpty();
        assertThat(WikimediaHttp.parseRetryAfter(null)).isEmpty();
    }

    @Test
    void doublesTheBackoffUpToOneMinute() {
        assertThat(WikimediaHttp.backoff(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(WikimediaHttp.backoff(5)).isEqualTo(Duration.ofSeconds(32));
        assertThat(WikimediaHttp.backoff(6)).isEqualTo(Duration.ofSeconds(60));
        assertThat(WikimediaHttp.backoff(30)).isEqualTo(Duration.ofSeconds(60));
    }
}
