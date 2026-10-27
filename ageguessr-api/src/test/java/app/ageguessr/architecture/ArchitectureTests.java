package app.ageguessr.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

/**
 * First ArchUnit ruleset for the project — kept deliberately minimal (three rules)
 * rather than over-engineered, per docs/testing-strategy.md's own "seuil informatif au
 * démarrage, à durcir manuellement" framing. More rules should be added as later
 * features surface real needs, not preemptively.
 */
@AnalyzeClasses(packages = "app.ageguessr")
class ArchitectureTests {

    @ArchTest
    static final ArchRule features_do_not_depend_on_each_other =
            slices().matching("app.ageguessr.features.(*)..").should().notDependOnEachOther();

    @ArchTest
    static final ArchRule entities_live_in_the_feature_root_package = classes()
            .that()
            .areAnnotatedWith(Entity.class)
            .should()
            .resideInAPackage("app.ageguessr.features.*");

    /**
     * Outbound HTTP (Wikidata, Commons) belongs to the offline people ingestion only: a
     * game request must never wait on, or leak anything to, a third-party service.
     */
    @ArchTest
    static final ArchRule only_the_people_ingestion_makes_outbound_http_calls = noClasses()
            .that()
            .resideOutsideOfPackage("app.ageguessr.features.people.IngestPeople..")
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.springframework.web.client.RestClient");

    /**
     * config/ wires the application; features plug into it, never the reverse. Shared
     * constants (e.g. JWT claim names) belong in shared/.
     */
    @ArchTest
    static final ArchRule config_does_not_depend_on_features = noClasses()
            .that()
            .resideInAPackage("app.ageguessr.config..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("app.ageguessr.features..");

    @ArchTest
    static final ArchRule handlers_do_not_depend_on_controllers = noClasses()
            .that()
            .haveSimpleNameEndingWith("Handler")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Controller");
}
