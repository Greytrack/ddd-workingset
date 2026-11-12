package sk.workingset.domain;

import java.util.Objects;

/** 最小分类元数据。共享策略由分类初始化时确定。 */
public record Classification(String id, SharingMode sharingMode) {
    public Classification {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("classificationId must not be blank");
        }
        Objects.requireNonNull(sharingMode, "sharingMode");
    }

    public enum SharingMode { SHARED, UNSHARED }
}
