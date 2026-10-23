package co.edu.unicolmayor.janussync.architecture;

import com.tngtech.archunit.junit.*;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "co.edu.unicolmayor.janussync", importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {
    @ArchTest
    static final ArchRule dominioIndependiente = classes().that().resideInAPackage("..domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..");
    @ArchTest
    static final ArchRule aplicacionIndependiente = classes().that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "..domain..", "..application..");
}
