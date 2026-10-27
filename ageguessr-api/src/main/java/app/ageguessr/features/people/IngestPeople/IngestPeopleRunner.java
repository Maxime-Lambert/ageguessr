package app.ageguessr.features.people.IngestPeople;

import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Entry point of the ingestion, active only under the "ingest" profile, which also
 * keeps the HTTP server from starting (application-ingest.properties): the process
 * ingests, logs its report and exits. The exit is explicit: connection pools and the
 * HTTP client keep non-daemon threads alive, so the JVM would otherwise hang after the
 * report (seen on the first real run).
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest -Dspring-boot.run.arguments=--limit=60
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest -Dspring-boot.run.arguments=--qid=Q42
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest -Dspring-boot.run.arguments="--offset=10000 --sample=200 --dry-run"
 * </pre>
 */
@Slf4j
@Component
@Profile("ingest")
class IngestPeopleRunner implements ApplicationRunner {

    private final IngestPeopleHandler handler;
    private final IngestProperties properties;
    private final ConfigurableApplicationContext context;

    IngestPeopleRunner(
            IngestPeopleHandler handler, IngestProperties properties, ConfigurableApplicationContext context) {
        this.handler = handler;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        IngestPeopleCommand command = command(args, properties.targetPhotos());
        log.info("People ingestion started: {}", command);
        long start = System.nanoTime();
        IngestReport report = handler.handle(command);
        log.info(
                "People ingestion finished in {} s: {}",
                Duration.ofNanos(System.nanoTime() - start).toSeconds(),
                report);
        // Closes the context (pools, clients) and ends the JVM with code 0. A failure
        // above throws instead, and Spring Boot exits with a non-zero code.
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    static IngestPeopleCommand command(ApplicationArguments args, int defaultTarget) {
        String qid = single(args, "qid");
        String limit = single(args, "limit");
        String offsetOption = single(args, "offset");
        String sampleOption = single(args, "sample");
        int target = limit == null ? defaultTarget : Integer.parseInt(limit);
        if (target <= 0) {
            throw new IllegalArgumentException("--limit must be positive, got " + target);
        }
        int offset = offsetOption == null ? 0 : Integer.parseInt(offsetOption);
        if (offset < 0) {
            throw new IllegalArgumentException("--offset must not be negative, got " + offset);
        }
        int sample = sampleOption == null ? 0 : Integer.parseInt(sampleOption);
        if (sampleOption != null && sample <= 0) {
            throw new IllegalArgumentException("--sample must be positive, got " + sample);
        }
        if (qid != null && (offsetOption != null || sampleOption != null)) {
            throw new IllegalArgumentException("--offset and --sample walk the list, meaningless with --qid");
        }
        if (offsetOption != null && sampleOption == null) {
            throw new IllegalArgumentException("--offset needs --sample: the balanced ingestion has no single list");
        }
        return new IngestPeopleCommand(target, qid, offset, sample, args.containsOption("dry-run"));
    }

    private static String single(ApplicationArguments args, String name) {
        List<String> values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (values.size() > 1) {
            throw new IllegalArgumentException("--" + name + " given more than once");
        }
        return values.getFirst();
    }
}
