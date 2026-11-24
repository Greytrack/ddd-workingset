package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.RowMark;

import java.util.Objects;

/**
 * 值对象：行·版次信息（RowRevisionInfo，Shared Kernel「业务对象 + 多版次」公共类型）。
 *
 * <pre>
 * «ValueObject 行·版次信息» RowRevisionInfo
 * - Integer revision     // 行所属版次号（R 编号）
 * - RowMark mark         // 行标记（失效/替换/修改/新增/不变）
 * - Long originRowId     // 原始行 id（仅 替换/修改 携带）
 * </pre>
 *
 * <p>
 * <b>为何在 Shared Kernel</b>：行与版次的对应关系（在本版次是新增 / 沿用 / 修改 / 替换 / 失效，以及由哪一行演变而来）是 多版次业务对象的通用语义——与 {@link Revision} /
 * {@link Revisionable} / {@link RevisionItem} 同族（用户裁定 2026-09）； 各 BC 的行（明细行 / 引用行等）直接复用本形态。形态取自 14 需求建模设计稿 2026-09 定稿（原
 * v4-alm 占位 {@code ItemRevisionInfo} 更名上移）。
 *
 * <p>
 * 例：需求的 {@code Reference} 以本类型承载「引用建立时的版次钉住」——挂出即钉住目标版次、原样沿用（{@code mark=不变} 口径）； 行版次追踪机制随「二、关联关系」轮接入。
 *
 * @param revision
 *            行所属版次号（可空＝未钉住版次，如目标未启用版次）
 * @param mark
 *            行标记（非空）
 * @param originRowId
 *            原始行 id（仅 替换/修改 携带；其余标记必须为空——fail-fast 守住形态）
 */
public record RowRevisionInfo(Integer revision, RowMark mark, Long originRowId) {

    public RowRevisionInfo {
        Objects.requireNonNull(mark, "mark");
        if (mark.requiresOriginRow() && originRowId == null) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.ORIGIN_ROW_REQUIRED,
                    mark.getDescription());
        }
        if (!mark.requiresOriginRow() && originRowId != null) {
            throw new DomainException(DomainErrorType.INVALID_ARGUMENT, SharedKernelResultCode.ORIGIN_ROW_FORBIDDEN,
                    mark.getDescription());
        }
    }

    /**
     * 便捷构造：钉住某版次、原样沿用（引用建立时口径）。
     *
     * @param revision
     *            钉住的版次号（可空＝目标未启用版次）
     */
    public static RowRevisionInfo ofPinned(Integer revision) {
        return new RowRevisionInfo(revision, RowMark.UNCHANGED, null);
    }
}
