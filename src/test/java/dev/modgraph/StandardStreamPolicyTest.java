package dev.modgraph;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.ACCESS_STANDARD_STREAMS;
import static org.assertj.core.api.Assertions.assertThat;

class StandardStreamPolicyTest {

    @Test
    void productionCodeMustNotUseStandardStreamsOrPrintStackTrace() {
        JavaClasses imported = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("dev.modgraph", "java.lang");

        assertThat(imported.size()).isPositive();

        ArchRule rule = noClasses()
                .that().resideInAnyPackage("dev.modgraph", "dev.modgraph..")
                .should(ACCESS_STANDARD_STREAMS);

        rule.check(imported);
    }
}
