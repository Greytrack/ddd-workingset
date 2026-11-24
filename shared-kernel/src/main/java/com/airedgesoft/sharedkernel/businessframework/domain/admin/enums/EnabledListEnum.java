package com.airedgesoft.sharedkernel.businessframework.domain.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 启用禁用列表枚举（v3 {@code com.airedgesoft.plm.common.enums.list.EnabledListEnum} 同名同值拷贝；v4 侧自有枚举，domain 禁引 v3）。
 */
@Slf4j
@Getter
@AllArgsConstructor
public enum EnabledListEnum implements BaseListEnum {

    ENABLED("Enabled", "启用", 1L), DISABLED("Disabled", "禁用", 0L),;

    /** 编码 */
    private final String apiId;

    /** 描述 */
    private final String desc;

    /** key */
    private final Long key;

    public static final Long ID = 1002L;

    public static EnabledListEnum of(Long key) {
        for (EnabledListEnum value : values()) {
            if (value.getKey().equals(key)) {
                return value;
            }
        }
        log.error("EnabledListEnum not exist, key:{}", key);
        return null;
    }

    public boolean isEnabled() {
        return this.key == 1L;
    }

    @Override
    public Long getListId() {
        return ID;
    }
}
