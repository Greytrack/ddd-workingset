package com.airedgesoft.lib.restful.batch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 批量契约全局配置（前缀 {@code airedge.restful.batch}）：{@code @BatchLimit} 未显式声明 {@code max} 时的缺省上限。
 */
@ConfigurationProperties(prefix = "airedge.restful.batch")
public class BatchProperties {

    /** 全局批量条数上限（缺省 200；端点可用 {@code @BatchLimit(max = N)} 覆盖）。 */
    private int max = 200;

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }
}
