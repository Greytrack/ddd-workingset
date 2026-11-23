package com.airedgesoft.sharedkernel.workingset.domain.event;

import java.util.Objects;

/**
 * 关系事件的公共元数据。Working Set 不拥有 Reference/Association 聚合，只发布事件；
 * {@code originalRelationId} 用于消费方回查原始关系的传播怀疑、类型、方向等信息。
 */
public record RelationEventMetadataEvent(String eventId, String originalRelationId, String originId,
                                         OriginType originType, long occurredAt) {

    public RelationEventMetadataEvent {
        requireText(eventId, "eventId");
        requireText(originalRelationId, "originalRelationId");
        requireText(originId, "originId");
        Objects.requireNonNull(originType, "originType");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    public enum OriginType {CREATION, EXTENSION, MERGE}
}
