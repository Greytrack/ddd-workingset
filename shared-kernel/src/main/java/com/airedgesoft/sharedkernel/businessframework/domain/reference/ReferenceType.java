package com.airedgesoft.sharedkernel.businessframework.domain.reference;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;

/**
 * 枚举：引用类型（ReferenceType，对齐 v3 {@code air_inst_tab_reference.reference_type} 注释口径）。
 *
 * <p>
 * 装配规则（裁决 4：v4 必须覆盖）：创建引用时按目标 Tracker 是否共享装配 {@link #DIRECT} / {@link #RELATIVE} （对齐 v3 {@code AddAction:1078-1084}——目标
 * Tracker 共享＝直接引用，否则相对引用）； {@link #DANGLE}（悬空）与 {@link #VERSION}（版本引用）只落枚举与持久化，装配规则挂账（工作集创建差异，见 context 挂账 1）。
 */
public enum ReferenceType {

    /** 直接引用（1）：目标 Tracker 为共享 Tracker。 */
    DIRECT(1),

    /** 相对引用（2）：目标 Tracker 非共享。 */
    RELATIVE(2),

    /** 悬空引用（3）：工作集创建处理差异；装配规则挂账。 */
    DANGLE(3),

    /** 版本引用（4）：装配规则挂账。 */
    VERSION(4);

    private final int code;

    ReferenceType(int code) {
        this.code = code;
    }

    /** 落库数字口径（对齐 v3 reference_type 列注释）。 */
    public int code() {
        return code;
    }

    /**
     * 按落库数字反查（未知 code fail-fast）。
     *
     * @param code
     *            落库数字（1–4）
     * @return 引用类型
     */
    public static ReferenceType fromCode(int code) {
        for (ReferenceType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.REFERENCE_TYPE_UNKNOWN,
                code);
    }
}
