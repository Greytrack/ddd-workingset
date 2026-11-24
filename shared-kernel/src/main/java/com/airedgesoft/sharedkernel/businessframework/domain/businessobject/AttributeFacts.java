package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

/**
 * 属性元数据<b>只读契约</b>（共享内核）：字段值装配与属性值校验族读取的属性事实。
 *
 * <p>
 * <b>不是模型</b>：本接口只有访问器，没有字段、没有装配、没有取数——属性元数据的<b>来源、投影与缓存全部在 bridge-v3</b> （{@code AttributeSnapshot} 实现本契约；v3 元数据直读实现在
 * {@code V3ClassificationModel}）。共享内核只声明「消费方需要读什么」。
 *
 * <p>
 * <b>过渡期定位</b>（用户拍板）：v4 分类元数据尚未重建，过渡模型 = bridge-v3 的 {@code ClassSnapshot} / {@code AttributeSnapshot}，v4 侧不再持有分类模型（原
 * 28 字段属性定义、分类视图实现均已下沉/删除）。
 *
 * <p>
 * <b>退出条件</b>：v4 分类元数据重建后，元数据模型由 v4 自有类型承担，本契约随属性值校验族一并评估去留。
 */
public interface AttributeFacts {

    /** 属性 id（元数据节点 id）。 */
    Long id();

    /** 属性 apiId（v3 {@code APIID}）。 */
    String apiId();

    /** 属性显示名（校验报错文案用）。 */
    String name();

    /** 落库列名（v3 {@code dbName}）：创建 / 修改契约以列名寻址。 */
    String column();

    /** 属性类型键（v3 {@code AttributeTypeEnum} 的 intKey）。 */
    Integer typeKey();

    /** 是否必填。 */
    boolean required();

    /** 是否只读。 */
    boolean readOnly();

    /** 创建期是否可见（必填默认值填充 / 必填校验的候选过滤）。 */
    boolean addVisible();

    /** 是否变更控制（版次字段分桶判据）。 */
    boolean versioned();

    /** 变更后是否可触发传播怀疑（v3 { AttributeNode#getSuspected}；与「变更控制」一起构成传播门禁）。 */
    boolean canSuspected();

    /** 是否多选列表（创建期需做 itemKey 规范化）。 */
    boolean multipleList();

    /** 列表 id（单选 / 多选 / 级联列表；非列表为空）。 */
    Long listId();

    /** 文本规则 id。 */
    Long textRule();

    /** 最大输入长度。 */
    Integer maxInputLength();

    /** 小数位长度。 */
    Integer decimalLength();

    /** 是否允许为 0（{@code YesOrNoListEnum} 键）。 */
    Integer canZero();

    /** 数值下限。 */
    Long minNumberValue();

    /** 数值上限。 */
    Long maxNumberValue();

    /** 图片格式限制（逗号分隔的列表项 key）。 */
    String formatLimit();

    /** 日期显示格式（列表项 key）。 */
    Long dateShowStyle();

    /** 日期规则 id（列表项 key）。 */
    Long dateRule();

    /** 可否多选（{@code YesOrNoListEnum} 键；图片 / 列表）。 */
    Integer canMultipleSelect();

    /** 单文件大小上限（原样串，如 {@code 10MB}）。 */
    String maxUploadFileSize();

    /** 上传文件数上限。 */
    Integer maxUploadFileNumber();

    /** 主属性 apiId（级联属性的「主」；非级联为空）。 */
    String mainApiId();

    /** 创建期默认值（已按「当下」求值；未配默认值为空）。 */
    Object defaultValueForCreate();
}
