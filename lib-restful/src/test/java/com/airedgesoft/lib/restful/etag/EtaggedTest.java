package com.airedgesoft.lib.restful.etag;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Etagged} 协议适配器单测（信封显式携带并发代数；payload 为无版本字段的纯数据 Q*）： wire 形态 = 载荷字段平铺 + 信封 {@code lockedVersion} 恰好出现一次； ETag
 * 恒为响应表示 hash，不再由 {@code lockedVersion} 派生。
 */
class EtaggedTest {

    /** 模拟 Q* 读模型：纯数据字段，无任何版本概念（应用层不感知协议）。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record QTask(String taskId, String title, String status) {
    }

    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach
    void reset() {
        EtagContext.clear();
    }

    @Test
    void serialization_flattensPayloadExactly_once() throws Exception {
        // wire 上 lockedVersion 恰好一次（由信封承载，payload 无该字段）
        String wrapped = mapper.writeValueAsString(Etagged.of(new QTask("623809242866016", "T", "ACTIVE"), 3L));
        assertThat(wrapped).isEqualTo(
                "{\"taskId\":\"623809242866016\",\"title\":\"T\",\"status\":\"ACTIVE\",\"lockedVersion\":3}");
    }

    @Test
    void serialization_nullVersionOmitted_byEnvelopeNonNull() throws Exception {
        String json = mapper.writeValueAsString(Etagged.of(new QTask("1", "T", null), null));
        assertThat(json).isEqualTo("{\"taskId\":\"1\",\"title\":\"T\"}");
    }

    @Test
    void lockedVersion_comesFromEnvelope_notPayload() {
        assertThat(Etagged.of(new QTask("1", "T", null), 5L).lockedVersion()).isEqualTo(5L);
        assertThat(Etagged.of(new QTask("1", "T", null), null).lockedVersion()).isNull();
        assertThat(Etagged.of(new QTask("1", "T", null), 3L).lockedVersion()).isEqualTo(3L);
    }

    @Test
    void ofResourceName_readsCurrentGenerationFromContext() {
        Object instance = new Object();
        EtagContext.withExpected("demoTasks/101", 2L, () -> EtagContext.recordLoaded("demoTasks/101", instance, 2L));
        // 编程式作用域已还原 → 无版本；再记录后可读
        assertThat(Etagged.from(new QTask("101", "T", null), "demoTasks/101").lockedVersion()).isNull();
        EtagContext.recordLoaded("demoTasks/101", instance, 4L);
        assertThat(Etagged.from(new QTask("101", "T", null), "demoTasks/101").lockedVersion()).isEqualTo(5L);
    }

    @Test
    void compactConstructor_rejectsNullResource() {
        assertThatThrownBy(() -> Etagged.of(null, 1L)).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resource");
    }
}
