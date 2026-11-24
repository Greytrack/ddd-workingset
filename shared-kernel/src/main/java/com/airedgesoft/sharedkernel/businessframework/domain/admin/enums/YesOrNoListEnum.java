package com.airedgesoft.sharedkernel.businessframework.domain.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 是与否枚举
 *
 * @author huanggs@airedgesoft.com
 * @date 2025/02/12
 */
@Slf4j
@Getter
@AllArgsConstructor
public enum YesOrNoListEnum implements BaseListEnum {

    YES("YES", "是", 1L), NO("NO", "否", 0L),;

    /** 编码 */
    private final String apiId;

    /** 描述 */
    private final String desc;

    /** key */
    private final Long key;

    public static final Long ID = 1001L;

    public static YesOrNoListEnum of(Long key) {
        for (YesOrNoListEnum value : values()) {
            if (value.getKey().equals(key)) {
                return value;
            }
        }
        return null;
    }

    @Override
    public Long getListId() {
        return ID;
    }

    /**
     * boolean转换为Key
     *
     * @return boolean
     */
    public static Long of(boolean flg) {
        return flg ? YES.getKey() : NO.getKey();
    }
}
