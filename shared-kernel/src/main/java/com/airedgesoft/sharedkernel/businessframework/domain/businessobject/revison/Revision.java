package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;

import java.util.Objects;

/**
 * 值对象：版次。
 *
 * <p>
 * 不可变：{@link #recordCheckOut} / {@link #recordCheckIn} 返回替换后的新实例。版次号用 {@link RevisionNumber} 值对象（带 {@code next()} 递进）。
 *
 * @param revisionNumber
 *            版次号
 * @param checkedOut
 *            是否签出
 * @param checkoutUser
 *            签出人；未签出 null
 * @param checkoutTime
 *            签出时间（epoch millis）；未签出 null
 * @param checkinUser
 *            签入人；未签入 null
 * @param checkinTime
 *            签入时间（epoch millis）；未签入 null
 */
public record Revision(RevisionNumber revisionNumber, boolean checkedOut, Long checkoutUser, Long checkoutTime,
        Long checkinUser, Long checkinTime) {

    public Revision {
        Objects.requireNonNull(revisionNumber, "revisionNumber");
    }

    /** 初始版次：未签出、未签入。 */
    public static Revision initial() {
        return new Revision(RevisionNumber.initial(), false, null, null, null, null);
    }

    /** 「创建即签入」初始版次：未签出、直接落签入态。 */
    public static Revision initialCheckedIn(Long checkinUser, long epochMilli) {
        return new Revision(RevisionNumber.initial(), false, null, null, checkinUser, epochMilli);
    }

    /** 下一版次：版次号 +1，未签出、未签入。 */
    public Revision next() {
        return new Revision(revisionNumber.next(), false, null, null, null, null);
    }

    /** 由持久化状态重建；供仓储装配使用。 */
    public static Revision reconstitute(RevisionNumber revisionNumber, boolean checkedOut, Long checkoutUser,
            Long checkoutTime, Long checkinUser, Long checkinTime) {
        return new Revision(revisionNumber, checkedOut, checkoutUser, checkoutTime, checkinUser, checkinTime);
    }

    /** 记录签出（时间取当前系统时钟）。 */
    public Revision recordCheckOut(Long user) {
        if (checkedOut) {
            throw new DomainException(SharedKernelResultCode.REVISION_ALREADY_CHECKED_OUT, revisionNumber.value());
        }
        return new Revision(revisionNumber, true, user, System.currentTimeMillis(), null, null);
    }

    /** 记录签入（时间取当前系统时钟）。 */
    public Revision recordCheckIn(Long user) {
        if (!checkedOut) {
            throw new DomainException(SharedKernelResultCode.REVISION_NOT_CHECKED_OUT, revisionNumber.value());
        }
        return new Revision(revisionNumber, false, checkoutUser, checkoutTime, user, System.currentTimeMillis());
    }

    /** 是否为签出态。 */
    public boolean isCheckedOut() {
        return checkedOut;
    }

    /** 是否处于草稿阶段（尚未签入过）。 */
    public boolean isDraftPhase() {
        return checkinTime == null;
    }
}
