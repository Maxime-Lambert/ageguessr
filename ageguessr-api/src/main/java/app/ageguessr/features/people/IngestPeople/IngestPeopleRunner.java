package app.ageguessr.features.people.IngestPeople;

import app.ageguessr.features.people.Continent;
import app.ageguessr.features.people.Gender;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
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
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=ingest -Dspring-boot.run.arguments="--exclude-occupations=Q937857 --age-bands=74-81,82+"
 * </pre>
 *
 * <p>Focus options, comma-separated, all optional ({@link IngestFocus}):
 * {@code --exclude-occupations} and {@code --only-occupations} (P106 QIDs),
 * {@code --cells} ("AFRICA/FEMALE"), {@code --age-bands} ("74-81", "82+").
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
        IngestFocus focus = new IngestFocus(
                list(args, "exclude-occupations"),
                list(args, "only-occupations"),
                list(args, "cells").stream().map(IngestPeopleRunner::cell).collect(Collectors.toSet()),
                list(args, "age-bands"));
        if (qid != null && !focus.isNone()) {
            throw new IllegalArgumentException("focus options are meaningless with --qid: naming someone is deliberate");
        }
        if (sampleOption != null && !focus.cells().isEmpty()) {
            throw new IllegalArgumentException("--cells needs the balanced ingestion, not --sample");
        }
        return new IngestPeopleCommand(target, qid, offset, sample, args.containsOption("dry-run"), focus);
    }

    /** "AFRICA/FEMALE" → that cell. */
    private static Cell cell(String text) {
        String[] parts = text.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("a cell is CONTINENT/GENDER, got " + text);
        }
        try {
            return new Cell(
                    Continent.valueOf(parts[0].toUpperCase(Locale.ROOT)), Gender.valueOf(parts[1].toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown cell " + text + ", continents " + Arrays.toString(Continent.values())
                    + ", genders " + Arrays.toString(Gender.values()), e);
        }
    }

    /** The comma-separated values of an option given at most once; empty when absent. */
    private static Set<String> list(ApplicationArguments args, String name) {
        String value = single(args, name);
        if (value == null) {
            return Set.of();
        }
        Set<String> values = Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(v -> !v.isEmpty())
                .collect(Collectors.toSet());
        if (values.isEmpty()) {
            throw new IllegalArgumentException("--" + name + " needs at least one value");
        }
        return values;
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
