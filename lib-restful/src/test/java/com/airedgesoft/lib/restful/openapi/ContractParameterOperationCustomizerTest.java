package com.airedgesoft.lib.restful.openapi;

import com.airedgesoft.lib.restful.dryrun.DryRun;
import com.airedgesoft.lib.restful.filter.Filterable;
import com.airedgesoft.lib.restful.orderby.Sortable;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.lib.restful.pagination.PaginationProperties;
import com.airedgesoft.lib.restful.softdelete.SoftDeleteProperties;
import com.airedgesoft.lib.restful.updatemask.ValidUpdateMask;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ContractParameterOperationCustomizer} 单测：协议参数文档从注解 / 配置推导（updateMask / filter / orderBy / validateOnly /
 * pageSize / pageToken / showDeleted），已声明参数不重复注入。
 */
class ContractParameterOperationCustomizerTest {

    private final ContractParameterOperationCustomizer customizer = new ContractParameterOperationCustomizer(
            new PaginationProperties(), enabledSoftDelete());

    @SuppressWarnings("unused")
    static class SampleController {

        @PatchMapping("/v1/samples/{id}")
        @ValidUpdateMask(whitelist = {"title", "description"})
        public Object patch() {
            return null;
        }

        @GetMapping("/v1/samples")
        @Filterable({"status", "title"})
        @Sortable({"title", "createTime"})
        public Page<Object> list() {
            return null;
        }

        @PostMapping("/v1/samples")
        @DryRun
        public Object create() {
            return null;
        }

        @GetMapping("/v1/samples/{id}")
        public Object get() {
            return null;
        }
    }

    private static SoftDeleteProperties enabledSoftDelete() {
        SoftDeleteProperties properties = new SoftDeleteProperties();
        properties.setEnabled(true);
        properties.setTables(List.of("demo_task_write"));
        return properties;
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new SampleController(), SampleController.class.getDeclaredMethod(methodName));
    }

    private static List<String> names(Operation operation) {
        return operation.getParameters() == null
                ? List.of()
                : operation.getParameters().stream().map(Parameter::getName).toList();
    }

    @Test
    void patch_injectsUpdateMaskWithWhitelist() throws NoSuchMethodException {
        Operation operation = customizer.customize(new Operation(), handler("patch"));
        assertThat(names(operation)).containsExactly("updateMask");
        assertThat(operation.getParameters().get(0).getDescription()).contains("description/title");
    }

    @Test
    void list_injectsFilterOrderByAndPaginationAndShowDeleted() throws NoSuchMethodException {
        Operation operation = customizer.customize(new Operation(), handler("list"));
        assertThat(names(operation)).contains("filter", "orderBy", "pageSize", "pageToken", "showDeleted");
    }

    @Test
    void create_injectsValidateOnly() throws NoSuchMethodException {
        Operation operation = customizer.customize(new Operation(), handler("create"));
        assertThat(names(operation)).containsExactly("validateOnly");
    }

    @Test
    void plainGet_injectsOnlyShowDeleted() throws NoSuchMethodException {
        Operation operation = customizer.customize(new Operation(), handler("get"));
        assertThat(names(operation)).containsExactly("showDeleted");
    }

    @Test
    void existingParameter_notDuplicated() throws NoSuchMethodException {
        Operation operation = new Operation();
        operation.addParametersItem(new Parameter().name("updateMask").in("query"));
        Operation customized = customizer.customize(operation, handler("patch"));
        assertThat(names(customized)).containsExactly("updateMask");
    }
}
