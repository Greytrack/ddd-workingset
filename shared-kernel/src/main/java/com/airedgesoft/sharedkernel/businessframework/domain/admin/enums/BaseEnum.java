package com.airedgesoft.sharedkernel.businessframework.domain.admin.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * 枚举基类
 *
 * @param <T>
 * @author jiangkun@airedgesoft.com
 */
public interface BaseEnum<T> {

    /**
     * 获取枚举code属性
     */
    T getCode();

    /**
     * 获取枚举desc属性
     */
    String getDesc();

    static <T extends BaseEnum<?>> String getValue(String key, Class<T> enumClass) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        T t = null;
        for (T each : enumClass.getEnumConstants()) {
            if (each.getCode().equals(key)) {
                t = each;
                break;
            }
        }
        return null != t ? t.getDesc() : null;
    }

    static <T extends BaseEnum<?>> T getEnum(String key, Class<T> enumClass) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        T t = null;
        for (T each : enumClass.getEnumConstants()) {
            if (each.getCode().equals(key)) {
                t = each;
                break;
            }
        }
        return t;
    }
}
