package architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import sk.common.error.AppException;

/**
 * AC-01：Working Set 编译期不依赖 ALM 的实现模块或业务聚合类型（A1/T4 §3.1）。
 */
@AnalyzeClasses(
        packages = {"sk.workingset", "alm.requirement"},
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_must_not_depend_on_app_exception =
            noClasses()
                    .that().resideInAnyPackage("sk.workingset.domain..", "alm.requirement.domain..")
                    .should().dependOnClassesThat().areAssignableTo(AppException.class);

    @ArchTest
    static final ArchRule working_set_must_not_depend_on_alm =
            noClasses()
                    .that().resideInAPackage("sk.workingset..")
                    .should().dependOnClassesThat().resideInAPackage("alm.requirement..");
}
