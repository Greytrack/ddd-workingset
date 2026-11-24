package com.airedgesoft.sharedkernel.businessframework.domain.admin.enums;

/**
 * 列表枚举基类
 *
 * @author huanggs@airedgesoft.com
 * @date 2025/03/05
 */
public interface BaseListEnum {

    Long getListId();

    String getApiId();

    Long getKey();

    default Integer getIntKey() {
        return getKey().intValue();
    }

    default String getStringKey() {
        return getKey().toString();
    }

}
