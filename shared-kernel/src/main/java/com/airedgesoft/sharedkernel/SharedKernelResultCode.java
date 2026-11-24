package com.airedgesoft.sharedkernel;


import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainResCode;

/**
 * shared-kernel 领域错误码（模块号 010 = shared-kernel 块；码段 300100001–300199999，9 位 `30{MMM}{NNNN}`，规范见
 * .lattice/standards/v4/v4-exception-handling.md §2.2）。
 */
public enum SharedKernelResultCode implements DomainResCode {

    BUILDER_NOT_REGISTERED(DomainErrorType.INVALID_ARGUMENT, 300100001, "分类未注册业务对象构造器: type=%s"),
    ROW_MARK_UNKNOWN(DomainErrorType.INVALID_ARGUMENT, 300100002, "未知的行标记数字标记：%s"),
    REVISION_NUMBER_INVALID(DomainErrorType.INVALID_ARGUMENT, 300100003, "版次号必须 >= %s，实际: %s"),
    REVISION_ALREADY_CHECKED_OUT(DomainErrorType.CONFLICT, 300100004, "版次已签出，不能重复签出: R%s"),
    REVISION_NOT_CHECKED_OUT(DomainErrorType.CONFLICT, 300100005, "版次未签出，不能签入: R%s"),
    FIELD_COLUMN_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100006, "FieldValue.column 不能为空"),
    REFERENCE_ENDPOINTS_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100007, "引用必须指定宿主与目标: sourceId=%s, targetId=%s"),
    ORIGIN_ROW_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100008, "行标记 %s 必须携带原始行 id"),
    ORIGIN_ROW_FORBIDDEN(DomainErrorType.INVALID_ARGUMENT, 300100009, "行标记 %s 不携带原始行 id"),
    ALREADY_CHECKED_OUT(DomainErrorType.CONFLICT, 300100010, "已签出，不能重复签出: %s"),
    NOT_LATEST_REVISION(DomainErrorType.CONFLICT, 300100011, "仅最新版次可签出，当前为历史版次视图: %s"),
    NO_REVISION_ACCESSIBLE(DomainErrorType.CONFLICT, 300100012, "尚无版次记录，内容不可访问: %s"),
    NO_REVISION_RECORD(DomainErrorType.CONFLICT, 300100013, "无版次记录: %s"),
    NOT_CHECKED_OUT(DomainErrorType.CONFLICT, 300100014, "未签出，不能修改版次数据 / 签入 / 取消签出: %s"),
    CREATE_TYPE_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100015, "CreateInput.type 不能为空"),
    NUMBER_VALUE_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100016, "编号值不能为空"),
    NUMBER_RULE_ID_INVALID(DomainErrorType.INVALID_ARGUMENT, 300100017, "编号规则 id 非法: %s"),
    NUMBER_RULE_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100018, "编号规则不能为空"),
    PREFETCH_COUNT_INVALID(DomainErrorType.INVALID_ARGUMENT, 300100019, "预取数量必须 ≥ 1: %s"),
    NUMBER_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100020, "编号不能为空"),
    NUMBER_CONSUMED(DomainErrorType.CONFLICT, 300100021, "编号已消耗: %s"),
    NUMBER_OCCUPIED(DomainErrorType.CONFLICT, 300100022, "编号已被占用: %s"),
    REVISION_NOT_MOUNTED(DomainErrorType.CONFLICT, 300100023, "尚无版次记录，内容不可访问"),
    MOUNTED_REVISION_NOT_FOUND(DomainErrorType.NOT_FOUND, 300100024, "装载版次不存在: R%s"),
    REVISION_MOUNT_DUPLICATED(DomainErrorType.CONFLICT, 300100025, "版次已存在，不能重复挂载: R%s"),
    BACK_POINTER_REVISION_NOT_FOUND(DomainErrorType.NOT_FOUND, 300100026, "回指版次不存在"),
    FIELD_NOT_NUMBER(DomainErrorType.INVALID_ARGUMENT, 300100027, "字段不是数值: %s = %s"),
    FIELD_NOT_INTEGER(DomainErrorType.INVALID_ARGUMENT, 300100028, "字段不是整数: %s = %s"),
    ATTRIBUTE_TYPE_UNSUPPORTED(DomainErrorType.INVALID_ARGUMENT, 300100029, "不支持的属性类型: %s"),
    ATTRIBUTE_VALUE_FORMAT_INVALID(DomainErrorType.INVALID_ARGUMENT, 300100030, "属性值格式非法（%s）: %s"),
    ORDER_INDEX_INVALID(DomainErrorType.INVALID_ARGUMENT, 300100031, "位置值非法: %s"),
    CREATE_WORKINGSET_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100032, "CreateInput.workingsetId 不能为空"),

    // ==================== 引用行公共域（独立聚合根落 shared-kernel，2026-11-19 用户裁定） ====================

    /** 引用方已删除（编辑守卫前置）。 */
    REFERENCE_SOURCE_DELETED(DomainErrorType.CONFLICT, 300100033, "引用方已删除: %s"),
    /** 引用编辑必须先签出（口径 15；未签出含历史版次一律拒绝）。 */
    REFERENCE_REQUIRES_CHECKOUT(DomainErrorType.CONFLICT, 300100034, "引用方未签出，不能编辑引用: 需求=%s, 当前版次=R%s"),
    /** 单次引用操作超过 200 行（口径 14）。 */
    REFERENCE_BATCH_LIMIT_EXCEEDED(DomainErrorType.INVALID_ARGUMENT, 300100035, "单次引用操作超过 200 行: %s"),
    /** 判重拒绝（裁决 5，推翻口径 12）：同一引用属性下同一目标不重复挂出。 */
    REFERENCE_DUPLICATED(DomainErrorType.CONFLICT, 300100036, "引用重复: 需求=%s, 目标=%s, 目标版次=%s"),
    /** 提交集合内部重复（裁决 5）。 */
    REFERENCE_INPUT_DUPLICATED(DomainErrorType.INVALID_ARGUMENT, 300100037, "提交的引用目标内部重复: 需求=%s"),
    /** TargetRevision 钉住态缺版次。 */
    REFERENCE_TARGET_REVISION_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100038, "钉住目标版次必须给出版次号: %s"),
    /** TargetRevision 跟随最新态携带版次。 */
    REFERENCE_TARGET_REVISION_FORBIDDEN(DomainErrorType.INVALID_ARGUMENT, 300100039, "跟随最新不携带版次号: %s"),
    /** 引用版次记录版次号非法。 */
    REFERENCE_REVISION_INVALID(DomainErrorType.INVALID_ARGUMENT, 300100040, "引用版次号必须 >= 1: %s"),
    /** 引用只支持 RowMark 三态。 */
    REFERENCE_MARK_UNSUPPORTED(DomainErrorType.INVALID_ARGUMENT, 300100041, "引用不支持该行标记: %s"),
    /** 引用类型未知。 */
    REFERENCE_TYPE_UNKNOWN(DomainErrorType.INVALID_ARGUMENT, 300100042, "未知的引用类型 code: %s"),

    // ==================== 关联行公共域（独立聚合根落 shared-kernel，2026-11-23 用户裁定） ====================

    /** 关联行两端必填。 */
    ASSOCIATION_ENDPOINTS_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100043,
            "关联行必须指定源与目标: sourceId=%s, targetId=%s"),
    /** 源端对象已删除。 */
    ASSOCIATION_SOURCE_DELETED(DomainErrorType.CONFLICT, 300100044, "关联源端已删除: %s"),
    /** 目标端对象已删除。 */
    ASSOCIATION_TARGET_DELETED(DomainErrorType.CONFLICT, 300100045, "关联目标端已删除: %s"),
    /** 端对象不存在。 */
    ASSOCIATION_END_NOT_FOUND(DomainErrorType.NOT_FOUND, 300100046, "关联端对象不存在: %s"),
    /** 端对象分类与声明的 type 不符。 */
    ASSOCIATION_END_TYPE_MISMATCH(DomainErrorType.INVALID_ARGUMENT, 300100047, "关联端对象分类不符: 对象=%s, 声明=%s, 实际=%s"),
    /** 待操作行缺席。 */
    ASSOCIATION_ROW_NOT_FOUND(DomainErrorType.NOT_FOUND, 300100048, "关联行不存在: %s"),
    /** 待操作行不属于该源对象。 */
    ASSOCIATION_ROW_NOT_OWNED(DomainErrorType.CONFLICT, 300100049, "关联行不属于该源对象: 行=%s, 源=%s"),
    /** 规则渲染名必填。 */
    ASSOCIATION_RULE_NAME_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100050, "关联规则必须给出渲染名"),
    /** 规则动作 ASSIGN 缺规则值。 */
    ASSOCIATION_RULE_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100051, "规则设置动作必须携带规则: 行=%s"),
    /** 合并基线版次不存在。 */
    ASSOCIATION_BASELINE_REVISION_NOT_FOUND(DomainErrorType.NOT_FOUND, 300100052, "合并基线版次不存在: 对象=%s, R%s"),
    /** 关联 wire 代码非法（类型/传播/规则动作/端）。 */
    ASSOCIATION_CODE_INVALID(DomainErrorType.INVALID_ARGUMENT, 300100053, "关联参数非法: %s=%s"),
    /** 目标已挂载（单条新增静默剔除后无新建行）。 */
    ASSOCIATION_ALREADY_MOUNTED(DomainErrorType.CONFLICT, 300100054, "关联目标已挂载: 源=%s, 目标=%s"),
    /** 版次动作结果形态非法（{@code RevisionChangeSet} 自校验）。 */
    REVISION_CHANGE_SET_INVALID(DomainErrorType.CONFLICT, 300100055, "版次动作结果形态非法: %s"),
    EVENT_TARGET_NOT_FOUND(DomainErrorType.NOT_FOUND, 300100033, "事件目标不存在: %s"),
    EVENT_STATE_INVALID(DomainErrorType.CONFLICT, 300100034, "事件状态不一致: %s"),
    PERSISTENCE_SERIALIZATION_FAILED(DomainErrorType.INTERNAL, 300100035, "持久化状态序列化失败: %s"),
    PERSISTENCE_STATE_INVALID(DomainErrorType.INTERNAL, 300100036, "持久化状态非法: %s"),
    // ==================== 属性值校验（v3 CommonResultCode 迁移：必填/文本/列表/数值/日期/图片/级联，2026-11-24） ====================

    /** 必填违例（v3 10001041）。 */
    COLUMN_REQUIRED(DomainErrorType.INVALID_ARGUMENT, 300100060, "属性：%s不能为空"),
    /** 文本规则 / 日期规则不合法（v3 10001042）。 */
    COLUMN_TEXT_RULE_MATCH_ERROR(DomainErrorType.INVALID_ARGUMENT, 300100061, "属性：%s的值不合法"),
    /** 文本 / 富文本长度超长（v3 10001043）。 */
    COLUMN_TEXT_LENGTH_ERROR(DomainErrorType.INVALID_ARGUMENT, 300100062,
            "属性：%s的值长度超长，系统允许最大长度为%s"),
    /** 单选列表项不在列表值中（v3 10001050）。 */
    COLUMN_LIST_VALUE_NOT_MATCH_ERROR(DomainErrorType.INVALID_ARGUMENT, 300100063, "属性：%s的值不在%s列表值中"),
    /** 单选列表项在列表中未启用（v3 10001053）。 */
    COLUMN_LIST_VALUE_UN_ENABLED_ERROR(DomainErrorType.INVALID_ARGUMENT, 300100064, "属性：%s的值在列表%s中未启用"),
    /** 数值小数位数超长（v3 10001046）。 */
    COLUMN_NUMBER_DIGITS_MATCH_ERROR(DomainErrorType.INVALID_ARGUMENT, 300100065,
            "属性：%s的值小数点位数超长，系统允许最大长度为%s"),
    /** 数值不允许为 0（v3 10001047）。 */
    COLUMN_NUMBER_CAN_NOT_ZERO_ERROR(DomainErrorType.INVALID_ARGUMENT, 300100066, "属性：%s的值不能为0"),
    /** 数值小于下限（v3 10001048）。 */
    COLUMN_NUMBER_CAN_NOT_LESS_ERROR(DomainErrorType.INVALID_ARGUMENT, 300100067, "属性：%s的值不能小于%s"),
    /** 数值大于上限（v3 10001049）。 */
    COLUMN_NUMBER_CAN_NOT_GREATER_ERROR(DomainErrorType.INVALID_ARGUMENT, 300100068, "属性：%s的值不能大于%s"),
    /** 开始日期晚于结束日期（v3 10001055）。 */
    COLUMN_START_DATE_NOT_GE_END_DATE_ERROR(DomainErrorType.INVALID_ARGUMENT, 300100069,
            "属性：%s 开始日期不能大于结束日期"),
    /** 图片不允许多选（v3 10001056）。 */
    COLUMN_IMAGE_CANNOT_MULTIPLE_SELECT(DomainErrorType.INVALID_ARGUMENT, 300100070, "属性：%s不允许多选"),
    /** 图片上传数量超限（v3 10001057）。 */
    COLUMN_IMAGE_UPLOAD_FILE_NUMBER_EXCEED(DomainErrorType.INVALID_ARGUMENT, 300100071, "属性：%s上传数量超出限制"),
    /** 图片格式不在允许列表（v3 10001058）。 */
    COLUMN_IMAGE_FORMAT_NOT_ALLOWED(DomainErrorType.INVALID_ARGUMENT, 300100072, "属性：%s上传格式不允许，允许格式为%s"),
    /** 图片单文件大小超限（v3 10001059）。 */
    COLUMN_IMAGE_FILE_SIZE_EXCEED(DomainErrorType.INVALID_ARGUMENT, 300100073, "属性：%s上传大小超出限制，允许最大为%s"),
    /** 级联从值不在主属性当前值的可选范围内（v3 10001070）。 */
    CASCADE_FROM_VALUE_NOT_ALLOWED_ERROR(DomainErrorType.INVALID_ARGUMENT, 300100074,
            "属性：%s的值不在主属性当前值的可选范围内");

    private final DomainErrorType type;
    private final int code;
    private final String desc;

    SharedKernelResultCode(DomainErrorType type, int code, String desc) {
        this.type = type;
        this.code = code;
        this.desc = desc;
    }

    @Override
    public DomainErrorType getType() {
        return type;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }
}
