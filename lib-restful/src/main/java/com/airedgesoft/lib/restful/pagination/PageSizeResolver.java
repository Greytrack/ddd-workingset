package com.airedgesoft.lib.restful.pagination;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;

/**
 * 集合查询 {@code pageSize} 语义的唯一落点（v4-restful-api.md §7；各 BC 禁止自行实现/硬编码）：
 *
 * <ol>
 * <li><b>默认值</b> = {@link PageSizeDefaultSource}（宿主实现：v3 智能规则「页面滚动加载数量」）＞ 回落
 * {@link PaginationProperties#getDefaultPageSize()}；</li>
 * <li><b>缺省（未传）或 {@code 0}</b> → 取默认值（AIP-158:63-64）；<b>负值或非数字</b> →
 * 400（{@link DomainErrorType#INVALID_ARGUMENT}，不静默回退）；</li>
 * <li><b>上限</b> = {@code max(配置上限, 默认值)} —— 保证系统配置的「页面滚动加载数量」（50/100/200/500）不被服务端上限截断； 超出 → 钳到上限。</li>
 * </ol>
 */
public class PageSizeResolver {

    private final PaginationProperties properties;
    private final PageSizeDefaultSource defaultSource;

    public PageSizeResolver(PaginationProperties properties, PageSizeDefaultSource defaultSource) {
        this.properties = properties;
        this.defaultSource = defaultSource;
    }

    /** 系统配置的单页默认条数（来源不可用时回落配置默认值，恒 &gt; 0）。 */
    public int defaultPageSize() {
        Integer configured = defaultSource == null ? null : defaultSource.defaultPageSize();
        return configured != null && configured > 0 ? configured : properties.getDefaultPageSize();
    }

    /** 求解客户端入参（{@code null} = 未传）为最终单页条数；{@code 0} → 默认；负值 → 400；超上限 → 钳到上限。 */
    public int resolve(Integer requested) {
        int defaultSize = defaultPageSize();
        if (requested == null || requested == 0) {
            return defaultSize;
        }
        if (requested < 0) {
            throw new DomainException(DomainRestCode.PAGE_SIZE_INVALID, requested);
        }
        return Math.min(requested, Math.max(properties.getMaxPageSize(), defaultSize));
    }
}
