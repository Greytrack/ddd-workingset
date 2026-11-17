package sk.workingset.application.event;

import java.util.Objects;

/** 请求关系服务建立新的 Association；不是 Association 已创建的领域事实。 */
public record AssociationCreationRequested(RelationEventMetadata metadata, AssociationEndpoint source,
                                           AssociationEndpoint target) {

    public AssociationCreationRequested {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }
}
