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

    @ArchTest
    static final ArchRule handlers_do_not_depend_on_controllers = noClasses()
            .that()
            .haveSimpleNameEndingWith("Handler")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Controller");
}
