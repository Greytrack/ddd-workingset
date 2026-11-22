package com.airedgesoft.sharekernel.productspace.domain.event;

import java.util.Objects;

/**
 * ProductSpace 聚合已创建；Tracker 与 WorkingSet 通过后续事件独立完成初始化。
 */
public record ProductSpaceCreatedEvent(Long productSpaceId) {
    public ProductSpaceCreatedEvent {
        Objects.requireNonNull(productSpaceId, "productSpaceId");
    }
}
