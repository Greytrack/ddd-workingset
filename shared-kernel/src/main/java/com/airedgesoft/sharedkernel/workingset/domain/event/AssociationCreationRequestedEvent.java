package com.airedgesoft.sharedkernel.workingset.domain.event;

import java.util.Objects;

/**
 * 请求关系服务建立新的 Association；不是 Association 已创建的领域事实。
 */
public record AssociationCreationRequestedEvent(RelationEventMetadataEvent metadata, AssociationEndpointEvent source,
                                                AssociationEndpointEvent target) {

    public AssociationCreationRequestedEvent {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }
}
