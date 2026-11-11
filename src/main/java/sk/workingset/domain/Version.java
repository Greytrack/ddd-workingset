package sk.workingset.domain;

/**
 * 并发版本：普通编辑推进，用于乐观锁；与业务 {@link Revision} 分离（T4 §3.2、P5）。
 */
public record Version(long value) {

    public Version {
        if (value < 0) {
            throw new IllegalArgumentException("version must not be negative");
        }
    }

    public static Version of(long value) {
        return new Version(value);
    }
}
