package com.mercatto;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ArchitectureBoundaryTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.mercatto");

    @Test
    void catalog_should_not_depend_on_orders_or_sellers() {
        noClasses().that().resideInAPackage("..catalog..")
            .should().dependOnClassesThat().resideInAnyPackage("..orders..", "..sellers..")
            .check(classes);
    }

    @Test
    void modules_should_be_free_of_cycles() {
        SlicesRuleDefinition.slices().matching("com.mercatto.(*)..")
            .should().beFreeOfCycles()
            .check(classes);
    }
}