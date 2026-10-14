package com.meuquarto.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class ArchitectureTests {

    private static final String ROOT = "com.meuquarto";
    private static final String FIXTURE_ROOT = "archfixture";

    @TestFactory
    List<DynamicTest> productionCodeFollowsTheArchitecture() {
        JavaClasses code = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);

        return ArchitectureRules.all(ROOT).stream()
                .map(rule -> DynamicTest.dynamicTest(rule.getDescription(), () -> rule.allowEmptyShould(true).check(code)))
                .toList();
    }

    /** The fixture in src/test/java/archfixture breaks each rule once; every rule must catch it. */
    @Test
    void everyRuleCatchesItsViolationInTheFixture() {
        JavaClasses fixture = new ClassFileImporter().importPackages(FIXTURE_ROOT);

        List<ArchRule> rules = List.of(
                ArchitectureRules.otherModulesUseOnlyTheApplicationLayerOf(FIXTURE_ROOT, "profile"),
                ArchitectureRules.domainDependsOnNoOtherLayer(FIXTURE_ROOT),
                ArchitectureRules.applicationDoesNotDependOnApi(FIXTURE_ROOT),
                ArchitectureRules.controllersLiveInApi(FIXTURE_ROOT),
                ArchitectureRules.repositoriesLiveInInfra(FIXTURE_ROOT),
                ArchitectureRules.sharedDependsOnNoModule(FIXTURE_ROOT),
                ArchitectureRules.modulesAreFreeOfCycles(FIXTURE_ROOT));

        for (ArchRule rule : rules) {
            EvaluationResult result = rule.evaluate(fixture);
            assertThat(result.hasViolation()).as("rule should fail on the fixture: %s", rule.getDescription()).isTrue();
        }
    }
}
