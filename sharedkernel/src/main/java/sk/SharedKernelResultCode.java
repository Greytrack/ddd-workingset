package sk;


import sk.common.error.DomainErrorType;
import sk.common.error.DomainResCode;

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
    ;

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
