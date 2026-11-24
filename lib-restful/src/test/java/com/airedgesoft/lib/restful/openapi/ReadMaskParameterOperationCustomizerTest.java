package com.airedgesoft.lib.restful.openapi;

import com.airedgesoft.lib.restful.readmask.ReadMaskAspect;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReadMaskParameterOperationCustomizer} 单测：GET 端点自动注入 {@code readMask} 参数文档（白名单从返回 record 组件派生）， 非 GET /
 * 返回类型派生不出白名单的端点不注入；已有该参数时不重复注入。
 */
class ReadMaskParameterOperationCustomizerTest {

    private final ReadMaskParameterOperationCustomizer customizer = new ReadMaskParameterOperationCustomizer();

    @SuppressWarnings("unused")
    static class SampleController {

        @GetMapping("/v1/samples/{id}")
        public QSampleView get() {
            return null;
        }

        @GetMapping("/v1/samples")
        public java.util.List<QSampleView> list() {
            return null;
        }

        @PatchMapping("/v1/samples/{id}")
        public QSampleView patch() {
            return null;
        }

        @GetMapping("/v1/schemas")
        public String schema() {
            return null;
        }
    }

    @SuppressWarnings("unused")
    record QSampleView(String name, String taskId, String title) {
    }

    private static HandlerMethod handler(String methodName) throws NoSuchMethodException {
        return new HandlerMethod(new SampleController(), SampleController.class.getDeclaredMethod(methodName));
    }

    @DisplayName("GET 返回 record：注入 readMask query 参数，description 含排序后的可选字段清单")
    @Test
    void customize_getWithRecordReturn_injectsReadMaskParameter() throws NoSuchMethodException {
        Operation operation = new io.swagger.v3.oas.models.Operation();

        Operation customized = customizer.customize(operation, handler("get"));

        List<Parameter> parameters = customized.getParameters();
        assertThat(parameters).hasSize(1);
        Parameter readMask = parameters.get(0);
        assertThat(readMask.getName()).isEqualTo(ReadMaskAspect.READ_MASK_PARAM);
        assertThat(readMask.getIn()).isEqualTo("query");
        assertThat(readMask.getRequired()).isFalse();
        // Set 无序 → 文档清单排序保证跨重启稳定（字典序 name/taskId/title）
        assertThat(readMask.getDescription()).contains("name/taskId/title").contains("未知字段 400").contains("name 恒保留");
    }

    @DisplayName("GET 返回 record 集合：元素类型组件派生白名单，同样注入")
    @Test
    void customize_getWithRecordCollection_injectsReadMaskParameter() throws NoSuchMethodException {
        Operation operation = new io.swagger.v3.oas.models.Operation();

        Operation customized = customizer.customize(operation, handler("list"));

        assertThat(customized.getParameters()).hasSize(1);
        assertThat(customized.getParameters().get(0).getName()).isEqualTo(ReadMaskAspect.READ_MASK_PARAM);
    }

    @DisplayName("非 GET 端点：不注入（运行时投影由切面全局生效，参数文档只在标准读端点声明）")
    @Test
    void customize_nonGetMethod_noParameter() throws NoSuchMethodException {
        Operation operation = new io.swagger.v3.oas.models.Operation();

        Operation customized = customizer.customize(operation, handler("patch"));

        assertThat(customized.getParameters()).isNull();
    }

    @DisplayName("GET 返回类型派生不出 record 白名单：不注入")
    @Test
    void customize_nonRecordReturn_noParameter() throws NoSuchMethodException {
        Operation operation = new io.swagger.v3.oas.models.Operation();

        Operation customized = customizer.customize(operation, handler("schema"));

        assertThat(customized.getParameters()).isNull();
    }

    @DisplayName("已有 readMask 参数（如宿主手工声明）：不重复注入")
    @Test
    void customize_existingReadMaskParameter_notDuplicated() throws NoSuchMethodException {
        Operation operation = new io.swagger.v3.oas.models.Operation();
        operation.addParametersItem(new Parameter().name(ReadMaskAspect.READ_MASK_PARAM).in("query"));

        Operation customized = customizer.customize(operation, handler("get"));

        assertThat(customized.getParameters()).hasSize(1);
    }
}
