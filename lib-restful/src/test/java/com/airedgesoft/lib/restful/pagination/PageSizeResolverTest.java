package com.airedgesoft.lib.restful.pagination;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code pageSize} 语义单测（v4-restful-api.md §7）：默认值来源优先级（系统配置 ＞ 回落）、缺省或 0 取默认、负值 → 400、
 * 超上限钳制、且上限不低于系统默认值（配置的「页面滚动加载数量」不被截断）。
 */
class PageSizeResolverTest {

    private static PaginationProperties properties(int defaultSize, int maxSize) {
        PaginationProperties p = new PaginationProperties();
        p.setDefaultPageSize(defaultSize);
        p.setMaxPageSize(maxSize);
        return p;
    }

    @Test
    void absent_usesSystemConfiguredDefault() {
        PageSizeResolver resolver = new PageSizeResolver(properties(20, 100), () -> 200);
        assertThat(resolver.defaultPageSize()).isEqualTo(200);
        assertThat(resolver.resolve(null)).isEqualTo(200);
    }

    @Test
    void unsetOrInvalidSystemValue_fallsBackToConfiguredDefault() {
        assertThat(new PageSizeResolver(properties(20, 100), () -> null).resolve(null)).isEqualTo(20);
        assertThat(new PageSizeResolver(properties(20, 100), () -> 0).resolve(null)).isEqualTo(20);
        assertThat(new PageSizeResolver(properties(20, 100), null).resolve(null)).isEqualTo(20);
    }

    @Test
    void zeroRequest_usesDefault() {
        // AIP-158:63-64：page_size=0 与未传等价，取默认（不报错）
        PageSizeResolver resolver = new PageSizeResolver(properties(20, 100), () -> 50);
        assertThat(resolver.resolve(0)).isEqualTo(50);
    }

    @Test
    void negativeRequest_rejectedWith400() {
        // AIP-158:68：负值 → INVALID_ARGUMENT
        PageSizeResolver resolver = new PageSizeResolver(properties(20, 100), () -> 50);
        assertThatThrownBy(() -> resolver.resolve(-5)).isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getType()).isEqualTo(DomainErrorType.INVALID_ARGUMENT);
    }

    @Test
    void aboveCap_clampedToCap() {
        PageSizeResolver resolver = new PageSizeResolver(properties(20, 100), () -> 50);
        assertThat(resolver.resolve(5000)).isEqualTo(100);
        assertThat(resolver.resolve(80)).isEqualTo(80);
    }

    @Test
    void capNeverTruncatesSystemConfiguredDefault() {
        // 智能规则配 500、服务端上限 100 → 有效上限抬到 500，避免配置值被截断
        PageSizeResolver resolver = new PageSizeResolver(properties(20, 100), () -> 500);
        assertThat(resolver.resolve(500)).isEqualTo(500);
        assertThat(resolver.resolve(1000)).isEqualTo(500);
    }
}
