package com.example.swapi.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/** The layering rules, written as tests so a violation fails the build. */
class ArchitectureTest {

    private static final String ROOT = "com.example.swapi";

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    @DisplayName("the classes were actually imported, so a passing rule means something")
    void thereIsSomethingToCheck() {
        // Without this, deleting the production code would make every rule below pass.
        assertThat(CLASSES).hasSizeGreaterThan(20);
    }

    @Test
    @DisplayName("the domain does not know Spring exists")
    void domainHasNoFrameworkDependencies() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta..",
                        "com.fasterxml..",
                        "tools.jackson..",
                        "com.github.benmanes..")
                .because("the domain is plain Java: it must be testable without a framework, "
                        + "and swapping Spring out must not touch it");

        rule.check(CLASSES);
    }

    @Test
    @DisplayName("the domain does not know about any adapter")
    void domainDoesNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..config..")
                .because("dependencies point inward; the domain is the innermost layer")
                .check(CLASSES);
    }

    @Test
    @DisplayName("the application layer talks to ports, never to an adapter")
    void applicationDoesNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..config..")
                .because("the application depends on the output port it owns, and the adapters "
                        + "depend on that same interface: that inversion is what lets SWAPI be "
                        + "replaced without touching the core")
                .check(CLASSES);
    }

    @Test
    @DisplayName("the inbound web adapter does not reach into the outbound SWAPI adapter")
    void adaptersDoNotDependOnEachOther() {
        noClasses()
                .that().resideInAPackage("..adapter.in..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
                .because("a controller that imported a SWAPI DTO would put upstream's shape in "
                        + "our public contract, which is the exact coupling the ports prevent")
                .check(CLASSES);
    }

    @Test
    @DisplayName("SWAPI response DTOs never escape the adapter that owns them")
    void swapiDtosStayInTheAdapter() {
        noClasses()
                .that().resideOutsideOfPackage("..adapter.out.swapi..")
                .should().dependOnClassesThat().resideInAPackage("..adapter.out.swapi.dto..")
                .because("uid, title, results-versus-result and snake_case are upstream's "
                        + "problems; they stop at SwapiMapper")
                .check(CLASSES);
    }

    @Test
    @DisplayName("the layering holds as a whole")
    void layersAreRespected() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy("..domain..")
                .layer("Application").definedBy("..application..")
                .layer("Adapters").definedBy("..adapter..")
                .layer("Config").definedBy("..config..")

                .whereLayer("Config").mayNotBeAccessedByAnyLayer()
                .whereLayer("Adapters").mayOnlyBeAccessedByLayers("Config")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Adapters", "Config")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers(
                        "Application", "Adapters", "Config")

                .as("config is the composition root - the only place allowed to see "
                        + "every layer, which is this project's main() wiring")
                .check(CLASSES);
    }

    @Test
    @DisplayName("nobody prints to the console instead of logging")
    void noConsoleOutput() {
        noClasses()
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .because("the host collects logs from SLF4J; System.out is invisible there")
                .check(CLASSES);
    }
}
