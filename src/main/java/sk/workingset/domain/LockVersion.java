package sk.workingset.domain;

/**
 * 乐观锁版本（用户 2026-11-11 术语裁定）：每次 command 处理后数据发生变动、事务提交时自动 +1；
 * 不需要业务调用方手动显式触发。
 *
 * <p>与业务 {@link Revision}（手动版次升级）和 {@link Version}（发布大版本）分开。
 * 写入以调用方读取到的 {@code LockVersion} 作前置证据；不一致时必须显式冲突。
 */
public record LockVersion(long value) {

    public LockVersion {
        if (value < 0) {
            throw new IllegalArgumentException("lockVersion must not be negative");
        }
    }

    public static LockVersion initial() {
        return new LockVersion(0);
    }

    public static LockVersion of(long value) {
        return new LockVersion(value);
    }

    /** 数据变动后的下一个乐观锁版本。 */
    public LockVersion next() {
        return new LockVersion(Math.addExact(value, 1L));
    }
}
