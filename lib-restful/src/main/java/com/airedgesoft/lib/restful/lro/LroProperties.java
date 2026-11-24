package com.airedgesoft.lib.restful.lro;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LRO 配置（前缀 {@code airedge.restful.lro}）。
 */
@ConfigurationProperties(prefix = "airedge.restful.lro")
public class LroProperties {

    /** 通用轮询 / 取消端点与触发切面开关（缺省 true）。 */
    private boolean enabled = true;

    /** 202 响应 Retry-After 缺省秒数（注解 {@code retryAfterSeconds} 优先；缺省 2）。 */
    private int retryAfterSeconds = 2;

    /** Redisson Store 的 Operation TTL（终态数据保留时长；缺省 24 小时）。 */
    private Duration operationTtl = Duration.ofHours(24);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public void setRetryAfterSeconds(int retryAfterSeconds) {
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public Duration getOperationTtl() {
        return operationTtl;
    }

    public void setOperationTtl(Duration operationTtl) {
        this.operationTtl = operationTtl;
    }
}
