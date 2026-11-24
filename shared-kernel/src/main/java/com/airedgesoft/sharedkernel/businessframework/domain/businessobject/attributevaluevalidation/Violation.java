package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;
import com.airedgesoft.plm.tools.infrastructure.core.global.ResCode;

/**
 * 属性值校验违例（对应 IDDD-ch5.3 改造定稿 {@code Violation <<record 违例承载>>}）。
 *
 * <p>
 * 承载口径与旧栈一致：<b>原 ResCode + args 映射</b>——错误码沿用旧空间 {@code CommonResultCode} 的枚举常量 （如 {@code COLUMN_REQUIRED}），args
 * 与旧栈占位顺序完全一致，应用层按首条违例转 {@code DomainException} 时 报错文案与旧栈 {@code AppException(resCode, args...)} 逐字对齐。
 *
 * @param code
 *            错误码（旧空间 ResCode 常量，与 v3 同名同义）
 * @param args
 *            错误码占位参数（与 v3 传参顺序一致）
 */
public record Violation(ResCode code, Object[] args) {

    @Override
    public String toString() {
        return "Violation{code=" + code + ", args=" + (args == null ? "[]" : java.util.Arrays.toString(args)) + "}";
    }
}
