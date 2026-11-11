package alm.requirement.domain;

import sk.workingset.domain.LockVersion;

/**
 * 乐观锁冲突：写入时携带的 {@code expectedLockVersion} 与聚合当前 {@code lockVersion} 不一致。
 *
 * <p>业务 {@code revision} 与发布大版本 {@code version} 都不参与乐观锁判定；
 * 冲突必须显式失败，不得静默覆盖。
 */
public class LockVersionConflictException extends RuntimeException {

    private final LockVersion expected;
    private final LockVersion actual;

    public LockVersionConflictException(LockVersion expected, LockVersion actual) {
        super("lock_version conflict: expected " + expected.value() + " but actual is " + actual.value());
        this.expected = expected;
        this.actual = actual;
    }

    public LockVersion expected() {
        return expected;
    }

    public LockVersion actual() {
        return actual;
    }
}
