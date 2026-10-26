package app.ageguessr.features.people.IngestPeople;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for Wikidata (via QLever) and Wikimedia Commons, used only by the people
 * ingestion, hence kept in this use case rather than in config/. Wikimedia requires a
 * descriptive User-Agent, and without timeouts a stalled SPARQL query would hang the
 * ingestion forever. Redirects are not followed: a redirect must never take a download
 * past the host allow-list of ImageDownloader.
 */
@Configuration
@EnableConfigurationProperties({WikimediaProperties.class, IngestProperties.class})
class WikimediaClientConfig {

    @Bean
    RestClient wikimediaRestClient(WikimediaProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));
        // RestClient.builder() rather than an injected RestClient.Builder: Spring Boot 4
        // only provides that bean with spring-boot-starter-restclient, and this client
        // needs none of Boot's customizations.
        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
    }

    @Bean
    Sleeper sleeper() {
        return Sleeper.REAL;
    }
}
