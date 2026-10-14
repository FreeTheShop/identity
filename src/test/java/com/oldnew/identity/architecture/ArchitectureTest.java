package com.oldnew.identity.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.onionArchitecture;

/**
 * Fails the build when the DDD / onion layering is broken.
 * <p>
 * {@code config} is the composition root: it wires every layer together at startup, so it may depend on all
 * of them (exempted below). The reverse is forbidden: no layer may depend on {@code config}.
 */
@AnalyzeClasses(packages = "com.oldnew.identity", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPlainJava = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "com.mongodb..", "org.bson..", "io.swagger..", "com.nimbusds..");

    @ArchTest
    static final ArchRule onionLayers = onionArchitecture()
            .domainModels("..domain.model..", "..domain.error..")
            .domainServices("..domain.port..")
            .applicationServices("..application..")
            .adapter("web", "..adapter.in.web..")
            .adapter("security", "..adapter.in.security..")
            .adapter("mongo", "..adapter.out.mongo..")
            .adapter("crypto", "..adapter.out.crypto..")
            .ignoreDependency(resideInAPackage("..config.."), alwaysTrue());

    @ArchTest
    static final ArchRule noLayerDependsOnTheCompositionRoot = noClasses()
            .that().resideInAnyPackage("..domain..", "..application..", "..adapter..")
            .should().dependOnClassesThat().resideInAPackage("..config..");
}
