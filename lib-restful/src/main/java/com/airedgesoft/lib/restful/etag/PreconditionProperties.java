package com.airedgesoft.lib.restful.etag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 写请求 {@code lockedVersion} 校验配置（前缀 {@code airedge.restful.precondition}）。
 *
 * <p>
 * 语义（v4-restful-api.md §6 / D13）：状态变更写请求必须在 body 携带 {@code lockedVersion}——缺失 / 非法 → 400；按 {@link EtagResource}
 * 身份绑定期望版本，不匹配 → 409。删除 / 恢复族不校验版本（决策 C7）。
 */
@ConfigurationProperties(prefix = "airedge.restful.precondition")
public class PreconditionProperties {

    /** 总开关（缺省 true）：关闭后 body {@code lockedVersion} 的校验与绑定一并停用（仅建议迁移期旧端点使用）。 */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
