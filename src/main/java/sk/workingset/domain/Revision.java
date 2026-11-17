package sk.workingset.domain;

import sk.common.error.DomainException;
import sk.common.error.WorkingsetResultCode;

/**
 * 业务版次（R12/CONCEPT-009）：只有显式升级才推进；普通编辑不改变。
 * 不作为乐观锁使用（T4 §3.2）。
 */
public record Revision(int value) {

    public Revision {
        if (value < 0) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "revision must not be negative");
        }
    }

    public static Revision of(int value) {
        return new Revision(value);
    }
}
