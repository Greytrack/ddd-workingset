package sk.workingset.application;

import java.util.Objects;

/**
 * 调用者身份/上下文（首切片最小实现：仅身份，不含权限模型）。
 */
public record Caller(String id) {

    public Caller {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("caller id must not be blank");
        }
    }

    public static Caller of(String id) {
        return new Caller(id);
    }
}
