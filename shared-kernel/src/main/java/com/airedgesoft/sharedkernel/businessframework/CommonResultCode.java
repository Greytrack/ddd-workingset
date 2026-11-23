package com.airedgesoft.sharedkernel.businessframework;

import com.airedgesoft.lib.exception.ResCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用模块异常返回枚举，用于通用模块异常返回信息 系统级的参考@com.airedgesoft.ae.infrastructure.core.global.GlobalResCode 范围：10001000-10001999
 * 通用【common】，主要包含通用的响应码，infrastructure模块中的响应码
 *
 * @author CJC
 * @date 2024/7/24
 */
@Getter
@AllArgsConstructor
public enum CommonResultCode implements ResCode {
    ADD_REFERENCE_RESOURCE_FROM_NOT_EMPTY(10001006, "添加引用目标资源来源不能为空"),
    ONLY_ALLOW_CHECK_OUT_USER_OPERATE(10001027, "只有签出人允许操作"),

    ATTRIBUTE_VALUE_REQUIRED(10001030, "属性不可为空"),
    ATTRIBUTE_VALUE_TOO_LONG(10001031, "属性值长度过长"),
    ATTRIBUTE_VALUE_TOO_BIG(10001033, "属性值过大"),
    ATTRIBUTE_VALUE_TOO_SMALL(10001034, "属性值过小"),
    ATTRIBUTE_VALUE_INVALID(10001036, "属性值非法"),
    AUTO_NUMBER_NOT_SET(10001037, "编号器未设置"),
    // %s未关联工作流
    SUBCLASS_NO_WORKFLOW(10001038, "%s未关联工作流"),
    DATA_OBJECT_NOT_EXIST(10001039, "当前对象在回收站，或者不存在"),
    // 参数[%s]缺失
    PARAMETER_MISSING(10001040, "参数[%s]缺失"),
    /**
     * 属性：%s不能为空
     */
    COLUMN_REQUIRED(10001041, "属性：%s不能为空"),
    /**
     * 属性：%s的值不合法
     */
    COLUMN_TEXT_RULE_MATCH_ERROR(10001042, "属性：%s的值不合法"),
    /**
     * 属性：%s的值长度超长，系统允许最大长度为%s
     */
    COLUMN_TEXT_LENGTH_ERROR(10001043, "属性：%s的值长度超长，系统允许最大长度为%s"),
    /**
     * 属性：%s的值不是数值类型
     */
    COLUMN_NOT_NUMBER_ERROR(10001045, "属性：%s的值不是数值类型"),
    /**
     * 属性：%s的值小数点位数超长，系统允许最大长度为%s
     */
    COLUMN_NUMBER_DIGITS_MATCH_ERROR(10001046, "属性：%s的值小数点位数超长，系统允许最大长度为%s"),
    /**
     * 属性：%s的值不能为0
     */
    COLUMN_NUMBER_CAN_NOT_ZERO_ERROR(10001047, "属性：%s的值不能为0"),
    /**
     * 属性：%s的值不能小于%s
     */
    COLUMN_NUMBER_CAN_NOT_LESS_ERROR(10001048, "属性：%s的值不能小于%s"),
    /**
     * 属性：%s的值不能大于%s
     */
    COLUMN_NUMBER_CAN_NOT_GREATER_ERROR(10001049, "属性：%s的值不能大于%s"),
    /**
     * 属性：%s的值不在%s列表值中
     */
    COLUMN_LIST_VALUE_NOT_MATCH_ERROR(10001050, "属性：%s的值不在%s列表值中"),
    /**
     * 属性：%s日期格式错误，正确格式为 %s
     */
    COLUMN_DATE_FORMAT_ERROR(10001052, "属性：%s日期格式错误，正确格式为 %s"),
    /**
     * 属性：%s的值在列表%s中未启用
     */
    COLUMN_LIST_VALUE_UN_ENABLED_ERROR(10001053, "属性：%s的值在列表%s中未启用"),
    /**
     * %s未找到对应的分类
     */
    COLUMN_TYPE_NOT_MATCH_ERROR(10001054, "%s未找到对应的分类"),
    /**
     * 属性：%s 开始日期不能大于结束日期
     */
    COLUMN_START_DATE_NOT_GE_END_DATE_ERROR(10001055, "属性：%s 开始日期不能大于结束日期"),
    /**
     * 属性：%s不允许多选图片
     */
    COLUMN_IMAGE_CANNOT_MULTIPLE_SELECT(10001056, "属性：%s不允许多选"),
    COLUMN_IMAGE_UPLOAD_FILE_NUMBER_EXCEED(10001057, "属性：%s上传数量超出限制"),
    COLUMN_IMAGE_FORMAT_NOT_ALLOWED(10001058, "属性：%s上传格式不允许，允许格式为%s"),
    COLUMN_IMAGE_FILE_SIZE_EXCEED(10001059, "属性：%s上传大小超出限制，允许最大为%s"),
    NO_PRIVILEGE_OPERATION_TYPE(10001060, "无%s权限，操作失败"),
    NO_PRIVILEGE_OPERATION_TYPE2(10001061, "%s无%s权限，操作失败"),
    /**
     * 不能拖动到自己下
     */
    DATA_OBJECT_CANNOT_DRAG_TO_SELF(10001068, "不能拖动到自己下"),
    /**
     * 不能拖动到自己的子节点下
     */
    DATA_OBJECT_CANNOT_DRAG_TO_CHILD(10001063, "不能拖动到自己的子节点下"),
    /**
     * %s页签不为空，删除失败
     */
    TAB_NOT_EMPTY_DELETE_FAILED(10001066, "%s页签不为空，删除失败"),
    /**
     * %s的%s页签不为空，删除失败
     */
    OBJECT_TAB_NOT_EMPTY_DELETE_FAILED(10001067, "%s的%s页签不为空，删除失败"),

    /**
     * 属性：%s的值不在主属性当前值的可选范围内
     */
    CASCADE_FROM_VALUE_NOT_ALLOWED_ERROR(10001070, "属性：%s的值不在主属性当前值的可选范围内"),
    ;

    /**
     * 编码
     */
    private final int code;
    /**
     * 描述
     */
    private final String desc;
}
