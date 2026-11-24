package com.airedgesoft.lib.restful.readmask;

import com.airedgesoft.lib.restful.etag.Etagged;
import com.airedgesoft.lib.restful.pagination.Page;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReadMaskWhitelists} 单测：从 Controller 方法返回类型<b>自动派生</b>投影白名单（信封解包 / 协议元数据剔除 / 非 record 空清单）——Tier A 零注解声明。
 */
class ReadMaskWhitelistsTest {

    /** 模拟读模型（对齐 demo {@code Q*} 形态：AIP-122 {@code name} 首字段 + {@code NON_NULL} 契约）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QTask(String name, String taskId, String title) {
    }

    /** 基本类型组件（剪枝侧恒保留，白名单仍声明可选）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QStep(String name, String stepId, boolean done) {
    }

    /** {@code _} 前缀协议元数据组件（{@code _access} 同型，§19.2）：不入白名单。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QAccess(String name, String title, @JsonProperty("_access") String access) {
    }

    /** 协议信封（非读模型）：即便 record 也不得派生投影白名单（置 null 会破坏其规范形态）。 */
    @SuppressWarnings("unused")
    static class ProtocolEnvelopeController {

        com.airedgesoft.lib.restful.lro.Operation getOperation() {
            return null;
        }
    }

    @SuppressWarnings("unused")
    static class SampleController {

        QTask get() {
            return null;
        }

        Page<QTask> list() {
            return null;
        }

        Etagged<QTask> enveloped() {
            return null;
        }

        Optional<QTask> optionalGet() {
            return null;
        }

        ResponseEntity<Page<QTask>> nestedGet() {
            return null;
        }

        Page<? extends QTask> wildcardList() {
            return null;
        }

        QStep stepGet() {
            return null;
        }

        QAccess accessGet() {
            return null;
        }

        String nonRecordGet() {
            return null;
        }

        void voidDelete() {
        }
    }

    @DisplayName("of：record 返回类型自动派生全部业务组件（name/taskId/title）白名单")
    @Test
    void of_recordReturnType_derivesAllBusinessComponents() {
        assertThat(ReadMaskWhitelists.of(method("get"))).containsExactlyInAnyOrder("name", "taskId", "title");
    }

    @DisplayName("of：Page 信封解包 items 元素类型派生白名单")
    @Test
    void of_pageWrapped_unwrapsItemType() {
        assertThat(ReadMaskWhitelists.of(method("list"))).containsExactlyInAnyOrder("name", "taskId", "title");
    }

    @DisplayName("of：Etagged 信封解包 payload 派生白名单")
    @Test
    void of_etaggedWrapped_unwrapsPayload() {
        assertThat(ReadMaskWhitelists.of(method("enveloped"))).containsExactlyInAnyOrder("name", "taskId", "title");
    }

    @DisplayName("of：Optional 信封解包内层载荷派生白名单")
    @Test
    void of_optionalWrapped_unwrapsPayload() {
        assertThat(ReadMaskWhitelists.of(method("optionalGet"))).containsExactlyInAnyOrder("name", "taskId", "title");
    }

    @DisplayName("of：嵌套信封（ResponseEntity<Page<T>>）逐层解包至读模型")
    @Test
    void of_nestedEnvelope_unwrapsThroughComposedTypes() {
        assertThat(ReadMaskWhitelists.of(method("nestedGet"))).containsExactlyInAnyOrder("name", "taskId", "title");
    }

    @DisplayName("of：通配符泛型（Page<? extends T>）解包至上界读模型")
    @Test
    void of_wildcardBound_unwrapsToUpperBound() {
        assertThat(ReadMaskWhitelists.of(method("wildcardList"))).containsExactlyInAnyOrder("name", "taskId", "title");
    }

    @DisplayName("of：基本类型组件（done）仍入白名单可选")
    @Test
    void of_primitiveComponent_stillSelectable() {
        assertThat(ReadMaskWhitelists.of(method("stepGet"))).containsExactlyInAnyOrder("name", "stepId", "done");
    }

    @DisplayName("of：协议元数据组件（_ 前缀，如 _access）剔除白名单")
    @Test
    void of_protocolMetadataComponent_excluded() {
        assertThat(ReadMaskWhitelists.of(method("accessGet"))).containsExactlyInAnyOrder("name", "title");
    }

    @DisplayName("of：非 record 返回类型（String）派生空白名单")
    @Test
    void of_nonRecordReturnType_emptyWhitelist() {
        assertThat(ReadMaskWhitelists.of(method("nonRecordGet"))).isEmpty();
    }

    @DisplayName("of：void 返回类型派生空白名单")
    @Test
    void of_voidReturnType_emptyWhitelist() {
        assertThat(ReadMaskWhitelists.of(method("voidDelete"))).isEmpty();
    }

    @DisplayName("of：method 为 null 时返回空白名单")
    @Test
    void of_nullMethod_emptyWhitelist() {
        assertThat(ReadMaskWhitelists.of(null)).isEqualTo(Set.of());
    }

    private static Method method(String name) {
        try {
            return SampleController.class.getDeclaredMethod(name);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    @DisplayName("投影白名单：协议信封（Operation）虽是 record 也不参与派生——置 null 会破坏其规范形态（Critical 回归锚）")
    @Test
    void of_protocolEnvelopeRecord_returnsEmptyWhitelist() throws Exception {
        Method method = ProtocolEnvelopeController.class.getDeclaredMethod("getOperation");
        assertThat(ReadMaskWhitelists.of(method)).isEmpty();
    }
}
