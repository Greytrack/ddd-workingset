package architecture;

import com.airedgesoft.lib.exception.AppException;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackages;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * AC-01：Working Set 编译期不依赖 ALM 的实现模块或业务聚合类型（A1/T4 §3.1）。
 */
@AnalyzeClasses(
        packages = {"com.airedgesoft.sharedkernel.workingset", "com.airedgesoft.sharedkernel.productspace",
                "com.airedgesoft.sharedkernel.tracker", "com.airedgesoft.alm.requirement", "bootstrap"},
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule product_space_inner_layers_must_not_depend_on_outer_layers =
            noClasses()
                    .that().resideInAnyPackage("com.airedgesoft.sharedkernel.productspace.domain..", "com.airedgesoft.sharedkernel.productspace.application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.airedgesoft.sharedkernel.productspace.infrastructure..", "com.airedgesoft.sharedkernel.productspace.adapters..",
                            "com.airedgesoft.sharedkernel.workingset.infrastructure..", "com.airedgesoft.sharedkernel.workingset.adapters..",
                            "com.airedgesoft.sharedkernel.tracker.infrastructure..", "com.airedgesoft.sharedkernel.tracker.adapters..");

    @ArchTest
    static final ArchRule alm_inner_layers_must_not_depend_on_infrastructure =
            noClasses()
                    .that().resideInAnyPackage("com.airedgesoft.alm.requirement.domain..", "com.airedgesoft.alm.requirement.application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.airedgesoft.alm.requirement.infrastructure..", "com.airedgesoft.alm.requirement.adapters..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_app_exception =
            noClasses()
                    .that().resideInAnyPackage("com.airedgesoft.sharedkernel.workingset.domain..", "com.airedgesoft.alm.requirement.domain..")
                    .should().dependOnClassesThat().areAssignableTo(AppException.class);

    @ArchTest
    static final ArchRule working_set_must_not_depend_on_alm =
            noClasses()
                    .that().resideInAPackage("com.airedgesoft.sharedkernel.workingset..")
                    .should().dependOnClassesThat().resideInAPackage("com.airedgesoft.alm.requirement..");

    @ArchTest
    static final ArchRule query_must_not_depend_on_command =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.airedgesoft.sharedkernel.workingset.application.query..", "com.airedgesoft.alm.requirement.application.query..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.airedgesoft.sharedkernel.workingset.application.command..", "com.airedgesoft.alm.requirement.application.command..");

    @ArchTest
    static final ArchRule command_must_not_depend_on_query =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.airedgesoft.sharedkernel.workingset.application.command..", "com.airedgesoft.alm.requirement.application.command..")
                    .should().dependOnClassesThat(
                            resideInAnyPackage(
                                    "com.airedgesoft.sharedkernel.workingset.application.query..", "com.airedgesoft.alm.requirement.application.query..")
                                    .and(resideOutsideOfPackages(
                                            "com.airedgesoft.sharedkernel.workingset.application.query.provider..",
                                            "com.airedgesoft.alm.requirement.application.query.provider..")));

    @ArchTest
    static final ArchRule query_application_must_not_depend_on_mybatis =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.airedgesoft.sharedkernel.workingset.application.query..", "com.airedgesoft.alm.requirement.application.query..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.baomidou..", "org.apache.ibatis..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_persistence_frameworks =
            noClasses()
                    .that().resideInAnyPackage("com.airedgesoft.sharedkernel.workingset.domain..", "com.airedgesoft.sharedkernel.productspace.domain..", "com.airedgesoft.alm.requirement.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.baomidou..", "org.apache.ibatis..", "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule repositories_must_not_be_transactional =
            noClasses()
                    .that().resideInAnyPackage("..infrastructure.repositories..")
                    .should().beAnnotatedWith(Transactional.class);

    @ArchTest
    static final ArchRule repository_methods_must_not_be_transactional =
            noMethods()
                    .that().areDeclaredInClassesThat().resideInAnyPackage("..infrastructure.repositories..")
                    .should().beAnnotatedWith(Transactional.class);

    @ArchTest
    static final ArchRule repositories_must_not_depend_on_illegal_state_exception =
            noClasses()
                    .that().resideInAnyPackage("..infrastructure.repositories..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("java.lang.IllegalStateException");

    @ArchTest
    static final ArchRule shared_kernel_repositories_must_not_depend_on_business_bc_types =
            noClasses()
                    .that().resideInAnyPackage("com.airedgesoft.sharedkernel.workingset.infrastructure.repositories..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.airedgesoft.alm.requirement..");

    @ArchTest
    static final ArchRule shared_kernel_infrastructure_must_not_depend_on_business_bc_types =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.airedgesoft.sharedkernel.workingset.infrastructure..", "com.airedgesoft.sharedkernel.tracker.infrastructure..",
                            "com.airedgesoft.sharedkernel.productspace.infrastructure..")
                    .should().dependOnClassesThat().resideInAnyPackage("com.airedgesoft.alm.requirement..");

    @ArchTest
    static final ArchRule composition_root_must_not_depend_on_business_bc_adapters_or_infrastructure =
            noClasses()
                    .that().resideInAPackage("bootstrap..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.airedgesoft.alm.requirement.adapters..", "com.airedgesoft.alm.requirement.infrastructure..");
}
