package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison;

import com.airedgesoft.lib.exception.DomainErrorType;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;
import com.airedgesoft.sharedkernel.businessframework.domain.businessobject.BusinessDataCarrier;

/**
 * 可版次化能力（can-do）：多版次业务对象的能力契约（跨聚合复用）。
 *
 * <p>
 * <b>读写模型（定稿：一条当前版次 + 三桶字段）</b>：聚合内恒一条指针版次（当前 / 工作版次），历史版次不进聚合、由查询侧投影。 写必须先签出、内容写在版次上（共享字段写聚合根
 * {@code shared}，经草稿提升），主对象不提供内容写。
 *
 * <p>
 * 本接口只表达「什么状态允许做什么」与编排骨架；版次状态推进复用 default 实现，具体业务的额外守卫（如「指针须为最高版次」）由聚合覆写对应方法补齐。
 */
public interface Revisionable extends BusinessDataCarrier {

    /** 指针指向的版次记录；无版次时返回 {@code null}。 */
    RevisionItem getCurrentRevisionItem();

    /**
     * 工作版次产出（聚合内部实体工厂）：由源版次自复制产出一条未流转的新版次记录（复制版次内容、版次号 +1）。
     *
     * @param source
     *            自复制源版次（当前版次）
     */
    RevisionItem newWorkingRevision(RevisionItem source);

    /**
     * 挂载版次记录并使其成为指针版次（创建期挂初始版次 / 签出期挂工作版次）。
     *
     * @param revisionItem
     *            版次记录
     */
    void mountRevision(RevisionItem revisionItem);

    /**
     * 签出：产出工作版次（版次号 +1）并记录签出态、移动指针。
     *
     * @param operatorId
     *            签出人
     */
    default void checkOut(Long operatorId) {
        preRevision();
        RevisionItem source = requireCurrentRevision();
        RevisionItem working = newWorkingRevision(source);
        working.setRevision(working.getRevision().recordCheckOut(operatorId));
        mountRevision(working);
        postRevision();
    }

    /**
     * 签入：当前工作版次记录签入态（不新增版次、不动内容）。
     *
     * @param operatorId
     *            签入人
     */
    default void checkIn(Long operatorId) {
        preRevision();
        RevisionItem working = requireWorkingRevision();
        working.setRevision(working.getRevision().recordCheckIn(operatorId));
        postRevision();
    }

    /** 当前是否处于签出态（指针版次处于签出态）。 */
    default boolean isCheckedOut() {
        RevisionItem current = getCurrentRevisionItem();
        return current != null && current.getRevision().isCheckedOut();
    }

    /** 基线版次（签出复制源 / 签入对照 / 取消签出回退目标）；无前序时为 {@code null}。 */
    default RevisionItem getBaselineRevisionItem() {
        return null;
    }

    /** 版次前置扩展点；default 空实现，由各业务按需覆写。 */
    default void preRevision() {
        // 默认无前置逻辑
    }

    /** 版次后置扩展点；default 空实现，由各业务按需覆写。 */
    default void postRevision() {
        // 默认无后置逻辑
    }

    /** 聚合身份描述（报错文案定位用，如「需求 123」）。 */
    String describe();

    /** 指针必须已挂载。 */
    private RevisionItem requireCurrentRevision() {
        RevisionItem current = getCurrentRevisionItem();
        if (current == null) {
            throw new DomainException(DomainErrorType.CONFLICT, SharedKernelResultCode.NO_REVISION_ACCESSIBLE,
                    describe());
        }
        return current;
    }

    /** 当前须为工作版次（未签出即无工作版次）。 */
    private RevisionItem requireWorkingRevision() {
        RevisionItem current = requireCurrentRevision();
        if (!current.getRevision().isCheckedOut()) {
            throw new DomainException(DomainErrorType.CONFLICT, SharedKernelResultCode.NOT_CHECKED_OUT, describe());
        }
        return current;
    }
}
