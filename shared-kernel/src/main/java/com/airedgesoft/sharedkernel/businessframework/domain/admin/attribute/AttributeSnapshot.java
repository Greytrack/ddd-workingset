package com.airedgesoft.sharedkernel.businessframework.domain.admin.attribute;

import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.AttributeTypeEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.enums.YesOrNoListEnum;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.AttributeFacts;
import lombok.Data;

/**
 * 属性快照（替代 v3 的 {@code Attribute}）：v3 属性元数据在 v4 侧的<b>唯一扁平投影</b>。
 *
 * <p>
 * 字段面按消费方实际读取收窄：上半部是判权路径读取的字段，下半部（{@code 分类元数据}）是分类元数据驱动的装配 / 校验路径读取的字段。 两者都只是 v3 {@code AttributeNode} 的字段拷贝，不含任何 v3
 * 类型外泄。
 *
 * <p>
 * <b>过渡期定位</b>（用户拍板）：v4 分类元数据尚未重建，本快照即分类 / 属性元数据的过渡模型——v4 侧不再持有分类模型 （原 28 字段属性定义与分类视图实现已下沉 bridge），共享内核只留只读契约。v4
 * 元数据重建后，本类随 bridge 退役，消费方换成 v4 自有模型。
 *
 * <p>
 * 同时实现共享内核只读契约 {@link AttributeFacts}：共享内核的字段值装配 / 校验族只认访问器，不认本快照。
 */
@Data
public class AttributeSnapshot implements AttributeFacts {

    /** 引用属性类型键（{@code AttributeTypeEnum.REFERENCE}）：v4 已去除引用能力，不作为可写属性参与装配。 */
    public static final Integer TYPE_REFERENCE = AttributeTypeEnum.REFERENCE.getKey().intValue();

    /** 多选列表类型键（{@code AttributeTypeEnum.MULTIPLE_LIST}）：创建期需做 itemKey 规范化。 */
    public static final Integer TYPE_MULTIPLE_LIST = AttributeTypeEnum.MULTIPLE_LIST.getKey().intValue();

    /** 属性 ID */
    private Long id;

    /** 属性 API ID */
    private String apiId;

    /** 属性名 */
    private String name;

    /** 属性物理列名 */
    private String dbName;

    /** 属性来源（同表属性 / 关联属性，取 v3 {@code SourceEnum} 的 intKey） */
    private Integer source;

    /** 关联属性的目标分类 ID（仅关联属性有值） */
    private Long referenceType;

    /** 关联属性的目标属性 ID（仅关联属性有值） */
    private Long referenceAttributeId;

    /** 属性最大长度 */
    private Integer maxLength;

    private Long tableId;

    private Integer readOnly;

    /** 创建期是否可见（{@code YesOrNoListEnum} 键） */
    private Integer addVisible;

    /** 是否预设属性（v3 {@code Attribute#isBuildInData}） */
    private boolean buildInData;

    private Long privilegeBasisRow;

    private AttributeSnapshot relatedAttribute;

    /** 是否启用（v3 Node#getEnabled） */
    private Long enabled;

    /** 排序（v3 AttributeNode#getRank） */
    private Long rank;

    /** 枚举列表 ID（v3 Attribute#getEnumList） */
    private Long enumList;

    // ==================== 分类元数据（装配 / 校验路径读取；过渡期字段面） ====================

    /** 属性类型键（v3 {@code AttributeNode#getAttributeType}，{@code AttributeTypeEnum} 的 intKey） */
    private Integer typeKey;

    /** 是否必填（{@code YesOrNoListEnum} 键） */
    private Integer required;

    /** 变更控制（{@code YesOrNoListEnum} 键）→ 版次字段分桶判据 {@link #versioned()} */
    private Integer changeControl;

    /** 变更后可触发传播怀疑（{@code YesOrNoListEnum} 键）→ {@link #canSuspected()} */
    private Integer suspected;

    /** 最大输入长度（富文本按附件字节合计；与 {@link #maxLength} 的存储列宽不是同一口径） */
    private Integer maxInputLength;

    /** 文本规则 id */
    private Long textRule;

    /** 小数位长度 */
    private Integer decimalLength;

    /** 是否允许为 0（{@code YesOrNoListEnum} 键） */
    private Integer canZero;

    /** 数值下限 */
    private Long minNumberValue;

    /** 数值上限 */
    private Long maxNumberValue;

    /** 图片格式限制（逗号分隔的列表项 key） */
    private String formatLimit;

    /** 日期显示格式（列表项 key） */
    private Long dateShowStyle;

    /** 日期规则 id（列表项 key） */
    private Long dateRule;

    /** 可否多选（{@code YesOrNoListEnum} 键；图片 / 列表） */
    private Integer canMultipleSelect;

    /** 单文件大小上限（v3 原样串，如 {@code 10MB}） */
    private String maxUploadFileSize;

    /** 上传文件数上限 */
    private Integer maxUploadFileNumber;

    /** 主属性 id（级联属性的「主」；非级联为空） */
    private Long mainAttributeId;

    /** 帮助 id（主属性作用域解析入参，v3 {@code BaseNode#getHelpId}） */
    private Long helpId;

    /** 主属性 apiId（分类视图装配期解析；非级联为空） */
    private String mainApiId;

    /** 创建期默认值（分类视图装配期按「当下」求值并转串形态；未配默认值为空） */
    private Object defaultValueForCreate;

    public String getAPIID() {
        return apiId;
    }

    /** 落库列名（＝v3 {@code dbName}）：创建 / 修改契约以列名寻址。 */
    @Override
    public String column() {
        return dbName;
    }

    @Override
    public String apiId() {
        return apiId;
    }

    @Override
    public Long id() {
        return id;
    }

    @Override
    public boolean readOnly() {
        return isYes(readOnly);
    }

    @Override
    public boolean addVisible() {
        return isYes(addVisible);
    }

    @Override
    public Long textRule() {
        return textRule;
    }

    @Override
    public Integer maxInputLength() {
        return maxInputLength;
    }

    @Override
    public Integer decimalLength() {
        return decimalLength;
    }

    @Override
    public Integer canZero() {
        return canZero;
    }

    @Override
    public Long minNumberValue() {
        return minNumberValue;
    }

    @Override
    public Long maxNumberValue() {
        return maxNumberValue;
    }

    @Override
    public String formatLimit() {
        return formatLimit;
    }

    @Override
    public Long dateShowStyle() {
        return dateShowStyle;
    }

    @Override
    public Long dateRule() {
        return dateRule;
    }

    @Override
    public Integer canMultipleSelect() {
        return canMultipleSelect;
    }

    @Override
    public String maxUploadFileSize() {
        return maxUploadFileSize;
    }

    @Override
    public Integer maxUploadFileNumber() {
        return maxUploadFileNumber;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Integer typeKey() {
        return typeKey;
    }

    @Override
    public boolean required() {
        return isYes(required);
    }

    @Override
    public boolean versioned() {
        return isYes(changeControl);
    }

    @Override
    public boolean canSuspected() {
        return isYes(suspected);
    }

    @Override
    public Long listId() {
        return enumList;
    }

    @Override
    public String mainApiId() {
        return mainApiId;
    }

    @Override
    public Object defaultValueForCreate() {
        return defaultValueForCreate;
    }

    @Override
    public boolean multipleList() {
        return TYPE_MULTIPLE_LIST.equals(typeKey);
    }

    /** 是否为引用属性（防御口径：投影层已过滤，消费方可用作断言）。 */
    public boolean reference() {
        return TYPE_REFERENCE.equals(typeKey);
    }

    private static boolean isYes(Integer flag) {
        return flag != null && YesOrNoListEnum.YES.getIntKey().equals(flag);
    }
}
