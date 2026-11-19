package architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
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
        packages = {"sk.workingset", "sk.productspace", "alm.requirement"},
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule product_space_inner_layers_must_not_depend_on_outer_layers =
            noClasses()
                    .that().resideInAnyPackage("sk.productspace.domain..", "sk.productspace.application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "sk.productspace.infrastructure..", "sk.productspace.adapters..",
                            "sk.workingset.infrastructure..", "sk.workingset.adapters..",
                            "sk.tracker.infrastructure..", "sk.tracker.adapters..");

    @ArchTest
    static final ArchRule alm_inner_layers_must_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("alm.requirement.domain..", "alm.requirement.application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "alm.requirement.infrastructure..", "alm.requirement.adapters..");

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

    @ArchTest
    static final ArchRule query_must_not_depend_on_command =
            noClasses()
                    .that().resideInAnyPackage(
                            "sk.workingset.application.query..", "alm.requirement.application.query..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "sk.workingset.application.command..", "alm.requirement.application.command..");

    @ArchTest
    static final ArchRule command_must_not_depend_on_query =
            noClasses()
                    .that().resideInAnyPackage(
                            "sk.workingset.application.command..", "alm.requirement.application.command..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage(
                                    "sk.workingset.application.query..", "alm.requirement.application.query..")
                                    .and(resideOutsideOfPackages(
                                            "sk.workingset.application.query.provider..",
                                            "alm.requirement.application.query.provider..")));

    @ArchTest
    static final ArchRule query_application_must_not_depend_on_mybatis =
            noClasses()
                    .that().resideInAnyPackage(
                            "sk.workingset.application.query..", "alm.requirement.application.query..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.baomidou..", "org.apache.ibatis..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_persistence_frameworks =
            noClasses()
                    .that().resideInAnyPackage("sk.workingset.domain..", "sk.productspace.domain..", "alm.requirement.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.baomidou..", "org.apache.ibatis..", "org.springframework.jdbc..");
}
