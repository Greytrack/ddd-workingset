package com.airedgesoft.lib.restful.path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RequestCollections} 单测：集合段派生的唯一口径——contextPath 剥离（部署 {@code /plm}）优先于前缀判定， 否则 §6.7 并发身份 / §20 字段开关 / §19 ABAC
 * 集合裁决在真实部署整体静默失效。
 */
class RequestCollectionsTest {

    @DisplayName("集合段派生：无 contextPath 的 v1 URI 取首个路径段")
    @Test
    void of_plainV1Uri_derivesCollection() {
        assertThat(RequestCollections.of("/v1/demoTasks/101", "")).isEqualTo("demoTasks");
        assertThat(RequestCollections.of("/v1/demoTasks:batchUpdate", null)).isEqualTo("demoTasks");
    }

    @DisplayName("集合段派生：部署 contextPath 剥离后才判定 v1 前缀（否则恒 null）")
    @Test
    void of_contextPathUri_stripsContextPathBeforePrefixCheck() {
        assertThat(RequestCollections.of("/plm/v1/demoTasks/101", "/plm")).isEqualTo("demoTasks");
        assertThat(RequestCollections.of("/plm/v1/demoSettings", "/plm")).isEqualTo("demoSettings");
        assertThat(RequestCollections.of("/plm/v1/demoTasks:batchDelete", "/plm")).isEqualTo("demoTasks");
    }

    @DisplayName("集合段派生：contextPath 未声明但 URI 带前缀段时从首个 /v1/ 起算（网关重写/forward 兜底）")
    @Test
    void of_unstrippedContextPath_fallsBackToFirstV1Segment() {
        assertThat(RequestCollections.of("/gateway/plm/v1/demoTasks/101", "")).isEqualTo("demoTasks");
    }

    @DisplayName("集合段派生：非 v1 前缀、根路径、空白段与 null 输入均返回 null")
    @Test
    void of_nonVersionedOrMalformed_returnsNull() {
        assertThat(RequestCollections.of("/demoTasks/101", "")).isNull();
        assertThat(RequestCollections.of("/v1/", "")).isNull();
        assertThat(RequestCollections.of("/", null)).isNull();
        assertThat(RequestCollections.of(null, "/plm")).isNull();
        assertThat(RequestCollections.of(new MockHttpServletRequest())).isNull();
    }

    @DisplayName("集合段派生：矩阵参数（;v=1）与百分号编码的冒号都不得污染集合键（否则 §20 精确串匹配失配）")
    @Test
    void of_matrixParamsAndEncodedColon_normalizesSegment() {
        assertThat(RequestCollections.of("/v1/demoTasks;v=1/101", "")).isEqualTo("demoTasks");
        assertThat(RequestCollections.of("/v1/demoTasks%3AbatchUpdate", "")).isEqualTo("demoTasks");
        assertThat(RequestCollections.of("/plm/v1/demoTasks;v=2:batchDelete", "/plm")).isEqualTo("demoTasks");
    }
}
