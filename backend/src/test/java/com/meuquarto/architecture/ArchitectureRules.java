package com.meuquarto.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.lang.ArchRule;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * Module and layer rules from ADR 0001 and ADR 0014, built for a root package so the same rules
 * can be checked against the real code and against fixtures that break them on purpose.
 */
final class ArchitectureRules {

    static final List<String> MODULES = List.of("identity", "profile", "media", "billing", "room");

    private ArchitectureRules() {}

    static List<ArchRule> all(String root) {
        List<ArchRule> rules = new ArrayList<>();
        for (String module : MODULES) {
            rules.add(otherModulesUseOnlyTheApplicationLayerOf(root, module));
        }
        rules.add(domainDependsOnNoOtherLayer(root));
        rules.add(applicationDoesNotDependOnApi(root));
        rules.add(controllersLiveInApi(root));
        rules.add(repositoriesLiveInInfra(root));
        rules.add(sharedDependsOnNoModule(root));
        rules.add(modulesAreFreeOfCycles(root));
        return rules;
    }

    static ArchRule otherModulesUseOnlyTheApplicationLayerOf(String root, String module) {
        String base = root + "." + module;
        return noClasses().that().resideOutsideOfPackage(base + "..")
                .should().dependOnClassesThat().resideInAnyPackage(base + ".api..", base + ".domain..", base + ".infra..")
                .as("other modules use only the application layer of '" + module + "'");
    }

    static ArchRule domainDependsOnNoOtherLayer(String root) {
        return noClasses().that().resideInAPackage(root + ".*.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        root + ".*.api..", root + ".*.application..", root + ".*.infra..")
                .as("domain depends on no api, application or infra code");
    }

    static ArchRule applicationDoesNotDependOnApi(String root) {
        return noClasses().that().resideInAPackage(root + ".*.application..")
                .should().dependOnClassesThat().resideInAPackage(root + ".*.api..")
                .as("application does not depend on api");
    }

    static ArchRule controllersLiveInApi(String root) {
        return classes().that().resideInAPackage(root + "..")
                .and().areAnnotatedWith(RestController.class).or().areAnnotatedWith(Controller.class)
                .should().resideInAPackage(root + ".*.api..")
                .as("controllers live in an api package");
    }

    static ArchRule repositoriesLiveInInfra(String root) {
        return classes().that().resideInAPackage(root + "..").and().areAssignableTo(Repository.class)
                .should().resideInAPackage(root + ".*.infra..")
                .as("repositories live in an infra package");
    }

    static ArchRule sharedDependsOnNoModule(String root) {
        String[] modules = MODULES.stream().map(m -> root + "." + m + "..").toArray(String[]::new);
        return noClasses().that().resideInAPackage(root + ".shared..")
                .should().dependOnClassesThat().resideInAnyPackage(modules)
                .as("shared depends on no business module");
    }

    static ArchRule modulesAreFreeOfCycles(String root) {
        return slices().matching(root + ".(*)..").should().beFreeOfCycles()
                .as("modules are free of cycles");
    }
}
