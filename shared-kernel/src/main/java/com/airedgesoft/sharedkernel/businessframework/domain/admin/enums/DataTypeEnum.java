package com.airedgesoft.sharedkernel.businessframework.domain.admin.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据类型
 *
 * @author zcc
 */
@Slf4j
@Getter
@AllArgsConstructor
public enum DataTypeEnum implements BaseListEnum {

    SHORT("Short", "短整型", 1L),
    INT("Int", "整型", 2L),
    LONG("Long", "长整型", 3L),
    CHAR("Char", "字符型", 4L),
    STRING("String", "字符串", 5L),
    DATE("Date", "日期", 6L),
    BOOLEAN("Boolean", "布尔型", 7L),
    FLOAT("Float", "浮点型", 8L),
    DOUBLE("Double", "双精度浮点型", 9L),;

    /**
     * 编码
     */
    private final String apiId;

    /**
     * 描述
     */
    private final String desc;

    private final Long key;

    public static final Long ID = 1003L;

    @Override
    public Long getListId() {
        return ID;
    }
}
