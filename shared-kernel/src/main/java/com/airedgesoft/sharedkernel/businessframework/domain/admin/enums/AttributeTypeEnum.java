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
public enum AttributeTypeEnum implements BaseListEnum {

    /***
     * ShortText 579000000000097 1 LongText 579000000000098 2 NumericalValue 579000000000099 3 SingleList
     * 579000000000100 4 MultipleList 579000000000101 5 Date 579000000000102 6 CompositeText 579000000000103 7
     * CompositeNumericalValue 579000000000104 8 StartAndEndDate 579000000000105 9 RichText 579000000000106 10 Title
     * 579000000000109 13 Icon 579000000000110 14 Virtual 579000000000111 15 AutoNumber 579000000000112 16 CombinedList
     * 0001,0002,0003
     */

    SHORT_TEXT("ShortText", "短文本", 1L, DataTypeEnum.STRING.getIntKey(), "varchar(512)"),
    LONG_TEXT("LongText", "长文本", 2L, DataTypeEnum.STRING.getIntKey(), "text"),
    NUMERICAL_VALUE("NumericalValue", "数值", 3L, DataTypeEnum.LONG.getIntKey(), "float8"),
    SINGLE_LIST("SingleList", "单选列表", 4L, DataTypeEnum.STRING.getIntKey(), "int8"),
    MULTIPLE_LIST("MultipleList", "多选列表", 5L, DataTypeEnum.STRING.getIntKey(), "text"),
    DATE("Date", "日期", 6L, DataTypeEnum.LONG.getIntKey(), "int8"),
    COMPOSITE_TEXT("CompositeText", "组合文本", 7L, DataTypeEnum.STRING.getIntKey(), "varchar(512)"),
    COMPOSITE_NUMERICAL_VALUE("CompositeNumericalValue", "组合数值", 8L, DataTypeEnum.STRING.getIntKey(), "varchar(512)"),
    START_AND_END_DATE("StartAndEndDate", "开始和结束日期", 9L, DataTypeEnum.LONG.getIntKey(), "int8"),
    RICH_TEXT("RichText", "富文本", 10L, DataTypeEnum.STRING.getIntKey(), "text"),
    IMAGES("Images", "图片", 11L, DataTypeEnum.STRING.getIntKey(), "text"),
    TITLE("Title", "标题", 13L, DataTypeEnum.STRING.getIntKey(), null),
    ICON("Icon", "图标", 14L, DataTypeEnum.STRING.getIntKey(), null),
    VIRTUAL("Virtual", "虚拟", 15L, DataTypeEnum.STRING.getIntKey(), null),
    NUMBER("Number", "编号", 16L, DataTypeEnum.STRING.getIntKey(), "varchar(255)"),
    AGGREGATION("Aggregation", "聚合", 20L, DataTypeEnum.STRING.getIntKey(), null),
    REFERENCE("Reference", "引用属性", 19L, DataTypeEnum.STRING.getIntKey(), null),
    TABLE("Table", "表格", 21L, DataTypeEnum.STRING.getIntKey(), null),
    CASCADE_SINGLE_LIST("CascadingSingleSelectList", "级联单选列表", 24L, DataTypeEnum.STRING.getIntKey(), "text"),
    CASCADE_MULTIPLE_LIST("CascadingMultiSelectList", "级联多选列表", 25L, DataTypeEnum.STRING.getIntKey(), "text");

    public static final Long ID = 1004L;
    /**
     * 编码
     */
    private final String apiId;
    /**
     * 描述
     */
    private final String desc;
    /**
     * key
     */
    private final Long key;
    private final Integer attributeDbType;

    private final String dbFieldDesc;

    public static AttributeTypeEnum of(Long key) {
        for (AttributeTypeEnum value : values()) {
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

}
