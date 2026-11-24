package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.revison;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.SharedKernelResultCode;

/**
 * 值对象：版次号。
 *
 * <p>
 * 唯一保留的标识族值对象——主键 / 外键 / 操作人标识一律裸 {@code Long}；版次号带递进语义（{@link #next()}）， 故包为值对象。
 */
public record RevisionNumber(int value) implements Comparable<RevisionNumber> {

    public RevisionNumber {
        if (value < 1) {
            throw new DomainException(SharedKernelResultCode.REVISION_NUMBER_INVALID, 1, value);
        }
    }

    /** 初始版次号（1）。 */
    public static RevisionNumber initial() {
        return new RevisionNumber(1);
    }

    /** 指定版次号。 */
    public static RevisionNumber of(int value) {
        return new RevisionNumber(value);
    }

    /** 下一版次号（+1）。 */
    public RevisionNumber next() {
        return new RevisionNumber(value + 1);
    }

    @Override
    public int compareTo(RevisionNumber other) {
        return Integer.compare(value, other.value);
    }
}
