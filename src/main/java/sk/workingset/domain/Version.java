package sk.workingset.domain;

/**
 * 业务对象发布的大版本（用户 2026-11-11 术语裁定）：字符串标识，**可以为空**；
 * 只有某个版次发布了大版本时才会出现。
 *
 * <p>与 {@link Revision}（手动触发的递增版次）和 {@link LockVersion}（乐观锁）严格区分：
 * 普通修改不改 {@code version}，也不自行产生大版本。
 */
public record Version(String value) {

    public Version {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException("version must not be blank when present");
        }
    }

    /** 尚未发布大版本。 */
    public static Version none() {
        return new Version(null);
    }

    public static Version of(String value) {
        return new Version(value);
    }

    /** 是否已发布大版本。 */
    public boolean isPresent() {
        return value != null;
    }
}
