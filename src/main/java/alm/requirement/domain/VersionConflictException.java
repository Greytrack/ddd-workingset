package alm.requirement.domain;

import sk.workingset.domain.Version;

/**
 * 并发版本冲突：写入时携带的 {@code expectedVersion} 与聚合当前 {@code version} 不一致。
 *
 * <p>业务 {@code Revision} 不参与乐观锁判定（T4 §3.2）；冲突必须显式失败，不得静默覆盖。
 */
public class VersionConflictException extends RuntimeException {

    private final Version expected;
    private final Version actual;

    public VersionConflictException(Version expected, Version actual) {
        super("version conflict: expected " + expected.value() + " but actual is " + actual.value());
        this.expected = expected;
        this.actual = actual;
    }

    public Version expected() {
        return expected;
    }

    public Version actual() {
        return actual;
    }
}
