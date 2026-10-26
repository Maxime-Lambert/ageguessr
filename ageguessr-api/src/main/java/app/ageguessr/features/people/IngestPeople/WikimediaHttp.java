package app.ageguessr.features.people.IngestPeople;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * HTTP calls to Wikimedia services, polite and resilient:
 * <ul>
 *   <li>a pause before every request, so the ingestion never hammers a shared service;
 *   <li>retries on 429 and 5xx, on network errors, and on the MediaWiki "maxlag" error -
 *       which comes back as an HTTP 200 whose body says {"error": {"code": "maxlag"}}, so
 *       the status alone is not enough (seen in the spike, plans/active-plan.md Phase 1);
 *   <li>the server's Retry-After when it gives one, otherwise exponential backoff.
 * </ul>
 */
@Component
class WikimediaHttp {

    static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Sleeper sleeper;
    private final Duration pause;
    private final int maxAttempts;

    @Autowired
    WikimediaHttp(
            @Qualifier("wikimediaRestClient") RestClient restClient,
            ObjectMapper objectMapper,
            WikimediaProperties properties) {
        this(restClient, objectMapper, Sleeper.REAL, Duration.ofMillis(properties.requestPauseMs()), properties.maxAttempts());
    }

    WikimediaHttp(RestClient restClient, ObjectMapper objectMapper, Sleeper sleeper, Duration pause, int maxAttempts) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.sleeper = sleeper;
        this.pause = pause;
        this.maxAttempts = maxAttempts;
    }

    JsonNode get(URI uri) {
        return call(uri, () -> restClient.get().uri(uri));
    }

    /** SPARQL queries are POSTed: they are too long for a URL. */
    JsonNode postForm(URI uri, MultiValueMap<String, String> form) {
        return call(uri, () -> restClient.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form));
    }

    private JsonNode call(URI uri, Supplier<RestClient.RequestHeadersSpec<?>> request) {
        String lastFailure = "no attempt";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            sleep(pause);
            Outcome outcome;
            try {
                outcome = request.get().exchange((req, res) -> read(res.getStatusCode(), res.getHeaders().getFirst("Retry-After"),
                        res.getBody().readAllBytes()));
            } catch (ResourceAccessException e) {
                outcome = Outcome.retry("network error: " + e.getMessage(), Optional.empty());
            }
            if (outcome.body() != null) {
                return outcome.body();
            }
            if (!outcome.retryable()) {
                throw new WikimediaUnavailableException(uri.getHost() + " refused the request: " + outcome.failure());
            }
            lastFailure = outcome.failure();
            if (attempt < maxAttempts) {
                sleep(outcome.retryAfter().orElse(backoff(attempt)));
            }
        }
        throw new WikimediaUnavailableException(
                uri.getHost() + " still failing after " + maxAttempts + " attempts: " + lastFailure);
    }

    private Outcome read(HttpStatusCode status, String retryAfter, byte[] bytes) {
        Optional<Duration> serverWait = parseRetryAfter(retryAfter);
        if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value() || status.is5xxServerError()) {
            return Outcome.retry("HTTP " + status.value(), serverWait);
        }
        if (!status.is2xxSuccessful()) {
            return Outcome.fail("HTTP " + status.value());
        }
        JsonNode body;
        try {
            body = objectMapper.readTree(new String(bytes, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            // Jackson 3 only throws unchecked exceptions. A truncated body is worth a retry.
            return Outcome.retry("unreadable JSON body", serverWait);
        }
        if ("maxlag".equals(body.path("error").path("code").asString(""))) {
            return Outcome.retry("maxlag", serverWait);
        }
        return Outcome.ok(body);
    }

    static Duration backoff(int attempt) {
        Duration wait = Duration.ofSeconds(1L << Math.min(attempt, 6));
        return wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait;
    }

    /** Only the delay-seconds form: Wikimedia never sends an HTTP date. Capped, a server cannot park us for hours. */
    static Optional<Duration> parseRetryAfter(String header) {
        if (header == null) {
            return Optional.empty();
        }
        try {
            long seconds = Long.parseLong(header.strip());
            if (seconds < 0) {
                return Optional.empty();
            }
            Duration wait = Duration.ofSeconds(seconds);
            return Optional.of(wait.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : wait);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void sleep(Duration duration) {
        try {
            sleeper.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WikimediaUnavailableException("interrupted while waiting for Wikimedia", e);
        }
    }

    private record Outcome(JsonNode body, boolean retryable, String failure, Optional<Duration> retryAfter) {

        static Outcome ok(JsonNode body) {
            return new Outcome(body, false, null, Optional.empty());
        }

        static Outcome retry(String failure, Optional<Duration> retryAfter) {
            return new Outcome(null, true, failure, retryAfter);
        }

        static Outcome fail(String failure) {
            return new Outcome(null, false, failure, Optional.empty());
        }
    }
}
