package com.airedgesoft.lib.restful.etag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link EtagSupport} 单测：只保留「{@code If-None-Match} 归一」与「{@code lockedVersion} ↔ 存储 {@code @Version} 换算」两类与协议头解析无关的能力。
 * {@code If-Match} 与逐条令牌解析已随「并发只依赖 body {@code lockedVersion}」的裁决整体移除。
 */
class EtagSupportTest {

    @Test
    void normalize_stripsWeakPrefixAndQuotes() {
        assertThat(EtagSupport.normalize("\"sha256-abc\"")).isEqualTo("sha256-abc");
        assertThat(EtagSupport.normalize("W/\"sha256-abc\"")).isEqualTo("sha256-abc");
        assertThat(EtagSupport.normalize("  sha256-abc  ")).isEqualTo("sha256-abc");
        assertThat(EtagSupport.normalize(null)).isNull();
        assertThat(EtagSupport.normalize(" ")).isNull();
    }

    @Test
    void storageVersionOf_isNamedInverseOfGeneration() {
        // 代数 N → 存储 @Version = N-1：body 携带代数路径的具名换算（规范 §6 禁手写 ±1）
        assertThat(EtagSupport.storageVersionOf(1L)).isEqualTo(0L);
        assertThat(EtagSupport.storageVersionOf(10L)).isEqualTo(9L);
        assertThat(EtagSupport.storageVersionOf(null)).isNull();
        assertThat(EtagSupport.generationOf(0L)).isEqualTo(1L);
        assertThat(EtagSupport.generationOf(null)).isEqualTo(1L);
        assertThat(EtagSupport.generationOf(EtagSupport.storageVersionOf(7L))).isEqualTo(7L);
    }
}
