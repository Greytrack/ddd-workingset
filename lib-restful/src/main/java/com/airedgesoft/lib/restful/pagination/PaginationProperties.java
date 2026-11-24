package com.airedgesoft.lib.restful.pagination;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 集合查询分页与游标配置（v4-restful-api.md §7；前缀 {@code airedge.restful.pagination}）。
 *
 * <p>
 * <b>单页默认条数的真实来源是系统智能规则「页面滚动加载数量」</b>（v3 {@code SmartRuleNode.scrollLoadSize}），v4 经 bridge-v3
 * {@code V3PageSizeDefaults} 注入 {@link PageSizeDefaultSource}；本类 {@link #getDefaultPageSize()} 只是<b>系统配置不可得时的回落值</b>。
 * 语义统一由 {@link PageSizeResolver} 执行（缺省 / 400 / 上限钳制），各 BC 禁止硬编码。游标密钥与 TTL 同样集中于此：生产必须覆盖
 * {@code page-token-secret}（默认仅开发密钥）并支持轮换。
 */
@ConfigurationProperties(prefix = "airedge.restful.pagination")
public class PaginationProperties {

    /** 回落默认条数缺省值（字段初始值与文档兜底描述的单一来源）。 */
    public static final int FALLBACK_DEFAULT_PAGE_SIZE = 20;

    /** 上限缺省值（字段初始值与文档兜底描述的单一来源）。 */
    public static final int FALLBACK_MAX_PAGE_SIZE = 100;

    /** 回落默认条数（智能规则不可得时使用）。 */
    private int defaultPageSize = FALLBACK_DEFAULT_PAGE_SIZE;

    /** 上限配置；有效上限 = {@code max(本值, 默认条数)}，保证系统配置的滚动加载数量不被截断。 */
    private int maxPageSize = FALLBACK_MAX_PAGE_SIZE;

    /** 游标加密密钥（AES-GCM；缺省仅开发密钥，生产必须覆盖）。 */
    private String pageTokenSecret = PageTokens.DEFAULT_DEV_SECRET;

    /** 游标有效期（缺省 30 分钟）。 */
    private Duration pageTokenTtl = Duration.ofMinutes(30L);

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public String getPageTokenSecret() {
        return pageTokenSecret;
    }

    public void setPageTokenSecret(String pageTokenSecret) {
        this.pageTokenSecret = pageTokenSecret;
    }

    public Duration getPageTokenTtl() {
        return pageTokenTtl;
    }

    public void setPageTokenTtl(Duration pageTokenTtl) {
        this.pageTokenTtl = pageTokenTtl;
    }
}
