package com.mercatto;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ArchitectureBoundaryTest {

    private static final List<String> BUSINESS_MODULES =
        List.of("users", "catalog", "orders", "sellers", "cart", "reviews");

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.mercatto");

    @Test
    void catalog_should_not_depend_on_orders_or_sellers() {
        noClasses().that().resideInAPackage("..catalog..")
            .should().dependOnClassesThat().resideInAnyPackage("..orders..", "..sellers..", "..cart..")
            .check(classes);
    }

    @Test
    void modules_should_be_free_of_cycles() {
        SlicesRuleDefinition.slices().matching("com.mercatto.(*)..")
            .should().beFreeOfCycles()
            .check(classes);
    }

    @Test
    void business_modules_should_not_depend_on_config() {
        noClasses().that().resideInAnyPackage("..users..", "..catalog..", "..orders..", "..sellers..", "..cart..")
            .should().dependOnClassesThat().resideInAPackage("..config..")
            .check(classes);
    }

    @Test
    void no_module_should_access_repository_package_of_another_module() {
        for (String module : BUSINESS_MODULES) {
            noClasses().that().resideOutsideOfPackage("..%s..".formatted(module))
                .should().dependOnClassesThat().resideInAPackage("..%s.repository..".formatted(module))
                .as("only module '" + module + "' may use its own repository package; "
                    + "repository is implementation detail (Contrato de Modularidade regra 3)")
                .check(classes);
        }
    }

    @Test
    void business_modules_should_only_reach_other_modules_through_service_event_or_domain() {
        for (String module : BUSINESS_MODULES) {
            String[] otherModulesInternals = BUSINESS_MODULES.stream()
                .filter(other -> !other.equals(module))
                .flatMap(other -> Stream.of("..%s.api..".formatted(other), "..%s.repository..".formatted(other)))
                .toArray(String[]::new);

            noClasses().that().resideInAPackage("..%s..".formatted(module))
                .should().dependOnClassesThat().resideInAnyPackage(otherModulesInternals)
                .as("module '" + module + "' must not depend on another module's api/repository packages; "
                    + "only <module>.service, <module>.event or <module>.domain are public API "
                    + "(Contrato de Modularidade regra 3/6)")
                .check(classes);
        }
    }

    @Test
    void serviceImpl_classes_should_be_package_private() {
        classes().that().haveSimpleNameEndingWith("ServiceImpl")
            .should().bePackagePrivate()
            .as("*ServiceImpl classes are implementation detail and must not be public "
                + "(Contrato de Modularidade regra 3)")
            .check(classes);
    }
}