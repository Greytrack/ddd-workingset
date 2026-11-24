package com.airedgesoft.sharedkernel.businessframework.domain.businessobject;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.Revision;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.Revisionable;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison.RowRevisionInfo;

/**
 * 值对象（enum）：行标记（RowMark）——行在版次间的变更方式（Shared Kernel「业务对象 + 多版次」公共类型）。
 *
 * <p>
 * <b>为何在 Shared Kernel</b>：任何多版次业务对象的行都按同一套口径随新版次演进——不是需求（或任何单一 BC）的专属语义， 故与 {@link Revision} / {@link Revisionable}
 * 同族落共享层（用户裁定 2026-09）。
 *
 * <p>
 * 行随版次演进的五种口径：
 *
 * <ul>
 * <li>{@link #INVALIDATED} 失效（code=4）——本版次中该行不再有效；</li>
 * <li>{@link #REPLACED} 替换（code=3）——本行替换了上一版次的某行（{@code originRowId} 指向被替换行）；</li>
 * <li>{@link #MODIFIED} 修改（code=2）——本行由上一版次的某行修改而来（{@code originRowId} 指向来源行）；</li>
 * <li>{@link #ADDED} 新增（code=1）——本版次新出现的行；</li>
 * <li>{@link #UNCHANGED} 不变（code=0）——自上一版次原样沿用。</li>
 * </ul>
 *
 * <p>
 * 数字标记口径（落库 / 协议传输用）：0＝不变（中性默认），1＝新增，2＝修改，3＝替换，4＝失效——按变更强度递增排列； 反查用 {@link #fromCode(int)}，未知 code 一律 fail-fast。
 *
 * <p>
 * 行版次追踪机制（新版本次推导各行标记）随各 BC「关联关系」轮接入；共享层先落定稿枚举与 {@link RowRevisionInfo} 形态。
 */
public enum RowMark {

    /** 失效：本版次中该行不再有效。 */
    INVALIDATED("失效", 4),

    /** 替换：替换上一版次的某行（originRowId 指向被替换行）。 */
    REPLACED("替换", 3),

    /** 修改：由上一版次的某行修改而来（originRowId 指向来源行）。 */
    MODIFIED("修改", 2),

    /** 新增：本版次新出现的行。 */
    ADDED("新增", 1),

    /** 不变：自上一版次原样沿用。 */
    UNCHANGED("不变", 0);

    private final String description;

    private final int code;

    RowMark(String description, int code) {
        this.description = description;
        this.code = code;
    }

    /** 行标记中文名（对齐设计稿口径：失效/替换/修改/新增/不变）。 */
    public String getDescription() {
        return description;
    }

    /** 行标记数字标记（不变=0，新增=1，修改=2，替换=3，失效=4；落库 / 协议传输口径）。 */
    public int getCode() {
        return code;
    }

    /**
     * 按数字标记反查枚举（未知 code fail-fast）。
     *
     * @param code
     *            数字标记（0-4）
     */
    public static RowMark fromCode(int code) {
        for (RowMark mark : values()) {
            if (mark.code == code) {
                return mark;
            }
        }
        throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.ROW_MARK_UNKNOWN, code);
    }

    /** 该标记是否要求携带原始行 id（仅 替换 / 修改 两种）。 */
    public boolean requiresOriginRow() {
        return this == REPLACED || this == MODIFIED;
    }
}
