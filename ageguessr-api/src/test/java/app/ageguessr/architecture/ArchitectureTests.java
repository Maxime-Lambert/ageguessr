package app.ageguessr.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;

/**
 * ArchUnit ruleset for the project — kept deliberately minimal rather than
 * over-engineered, per docs/testing-strategy.md's own "seuil informatif au
 * démarrage, à durcir manuellement" framing. More rules should be added as later
 * features surface real needs, not preemptively.
 */
@AnalyzeClasses(packages = "app.ageguessr")
class ArchitectureTests {

    /**
     * Three exceptions. Two from the game, which is built on the other features: on the
     * photos of people (age, curation status, box, public id) and on the hints of a year.
     * One from friends, which compares daily scores by the game's own rules (a complete
     * day, its maximum, the average, the streak: PlayerHistory) rather than a copy of them.
     * Never the reverse, see the next rules: neither people nor hints know anything of the
     * game, which is why a curation decision is read by the game rather than pushed to it,
     * and the game knows nothing of friends (docs/decisions/architecture.md).
     */
    @ArchTest
    static final ArchRule features_do_not_depend_on_each_other = slices().matching("app.ageguessr.features.(*)..")
            .should()
            .notDependOnEachOther()
            .ignoreDependency(
                    resideInAPackage("app.ageguessr.features.game.."),
                    resideInAPackage("app.ageguessr.features.people.."))
            .ignoreDependency(
                    resideInAPackage("app.ageguessr.features.game.."),
                    resideInAPackage("app.ageguessr.features.hints.."))
            .ignoreDependency(
                    resideInAPackage("app.ageguessr.features.friends.."),
                    resideInAPackage("app.ageguessr.features.game.."));

    @ArchTest
    static final ArchRule the_game_does_not_depend_on_friends = noClasses()
            .that()
            .resideInAPackage("app.ageguessr.features.game..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("app.ageguessr.features.friends..");

    @ArchTest
    static final ArchRule people_does_not_depend_on_the_game = noClasses()
            .that()
            .resideInAPackage("app.ageguessr.features.people..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("app.ageguessr.features.game..");

    @ArchTest
    static final ArchRule hints_do_not_depend_on_the_game = noClasses()
            .that()
            .resideInAPackage("app.ageguessr.features.hints..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("app.ageguessr.features.game..");

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

    /**
     * shared/ holds what several features use (the language of the site, errors, rate
     * limiting): it must not reach back into any of them, or two features would end up
     * depending on each other through it.
     */
    @ArchTest
    static final ArchRule shared_does_not_depend_on_features = noClasses()
            .that()
            .resideInAPackage("app.ageguessr.shared..")
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
