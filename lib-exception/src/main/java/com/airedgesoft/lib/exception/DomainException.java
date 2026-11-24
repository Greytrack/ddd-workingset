package com.airedgesoft.lib.exception;

import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DomainException extends APIException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** HTTP 状态码锚点（宏观语义），直译状态码。 */
    private final DomainErrorType type;

    /** 业务错误码（i18n 文案检索键 + {@code %s} 文案模板）。 */
    private final ResCode code;

    /** 字段级违规明细（可选载荷，缺省空——ProblemDetails 对空列表不写入扩展成员）。 */
    private List<FieldViolation> fieldViolations = List.of();

    /** 单参数抛法：错误码自带 {@link DomainErrorType}。 */
    public DomainException(DomainResCode code, Object... args) {
        super(code.getCode(), code.getDesc(), args(args));
        this.type = code.getType();
        this.code = code;
    }

    /** 单参数抛法（保留原因链）。 */
    public DomainException(DomainResCode code, Throwable cause, Object... args) {
        super(code.getCode(), code.getDesc(), args(args));
        initCause(cause);
        this.type = code.getType();
        this.code = code;
    }

    /** 双参数抛法：HTTP 语义与业务码分开指定。 */
    public DomainException(DomainErrorType type, ResCode code, Object... args) {
        super(code.getCode(), code.getDesc(), args(args));
        this.type = type;
        this.code = code;
    }

    /** 双参数抛法（保留原因链）。 */
    public DomainException(DomainErrorType type, ResCode code, Throwable cause, Object... args) {
        super(code.getCode(), code.getDesc(), args(args));
        initCause(cause);
        this.type = type;
        this.code = code;
    }

    private static Object[] args(Object[] args) {
        return args == null ? new Object[0] : args;
    }

    /** HTTP 状态码锚点（宏观语义）。 */
    public DomainErrorType getType() {
        return type;
    }

    /** 业务错误码契约（i18n 文案检索键；{@code getCode()} 继承自 APIException，即 {@code resCode.getCode()}）。 */
    public ResCode getResCode() {
        return code;
    }

    /** 文案：{@code %s} 模板经 args 格式化（AppException 同款；格式化失败回退原文）。 */
    @Override
    public String getMessage() {
        try {
            if (getArgs() != null && getArgs().length > 0) {
                return String.format(code.getDesc(), getArgs());
            }
            return code.getDesc();
        } catch (Exception e) {
            return super.getMessage();
        }
    }

    /** 流式携带字段级违规明细（403 越权字段 / 400 自定义字段校验等；ProblemDetails 写入 problem+json 扩展成员 {@code fieldViolations}）。 */
    public DomainException withFieldViolations(List<FieldViolation> fieldViolations) {
        this.fieldViolations = fieldViolations == null ? List.of() : List.copyOf(fieldViolations);
        return this;
    }

    /** 字段级违规明细（只读、保序；未携带时为空列表）。 */
    public List<FieldViolation> getFieldViolations() {
        return fieldViolations;
    }
}
