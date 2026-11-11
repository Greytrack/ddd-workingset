package sk.workingset.domain;

import java.util.Objects;

/**
 * 所有业务对象聚合的唯一标识（用户 2026-11-11 裁定：LocalId 即业务聚合标识，RequirementId = LocalId）。
 * <p>由 {@code IdentifierGenerator} 以雪花 ID 产生（FACT-005）；派生时预分配，首次 COW 复用，
 * COW 前后不变（T3/A2、INV-040）。
 */
public record LocalId(String value) {

    public LocalId {
        Objects.requireNonNull(value, "localId");
        if (value.isBlank()) {
            throw new IllegalArgumentException("localId must not be blank");
        }
    }

    public static LocalId of(String value) {
        return new LocalId(value);
    }
}
