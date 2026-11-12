package app.ageguessr.features.hints.ImportHints;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Entry point of the hint import, active only under the "import-hints" profile, which
 * also keeps the HTTP server from starting (application-import-hints.properties). The
 * process imports, logs its report and exits; the exit is explicit for the same reason as
 * IngestPeopleRunner: non-daemon threads would otherwise keep the JVM alive.
 *
 * <pre>
 * ./mvnw spring-boot:run -Dspring-boot.run.profiles=import-hints
 * </pre>
 *
 * <p>{@code app.hints.import.retire-absent=true} also retires the hints whose key the files
 * no longer hold (see {@link ImportHintsHandler}), for an extraction that replaces the
 * previous one.
 */
@Slf4j
@Component
@Profile("import-hints")
class ImportHintsRunner implements ApplicationRunner {

    private final HintFileReader reader;
    private final ImportHintsHandler handler;
    private final ConfigurableApplicationContext context;
    private final boolean retireAbsent;

    ImportHintsRunner(
            HintFileReader reader,
            ImportHintsHandler handler,
            ConfigurableApplicationContext context,
            @Value("${app.hints.import.retire-absent}") boolean retireAbsent) {
        this.reader = reader;
        this.handler = handler;
        this.context = context;
        this.retireAbsent = retireAbsent;
    }

    @Override
    public void run(ApplicationArguments args) {
        ImportHintsReport report = handler.handle(reader.readAll(), retireAbsent);
        log.info("Hint import finished: {}", report);
        // A failure above throws instead, and Spring Boot exits with a non-zero code.
        System.exit(SpringApplication.exit(context, () -> 0));
    }
}
