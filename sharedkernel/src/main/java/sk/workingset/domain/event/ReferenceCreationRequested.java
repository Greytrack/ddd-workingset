package sk.workingset.domain.event;

import java.util.Objects;

/** 请求关系服务建立新的 Reference；不是 Reference 已创建的领域事实。 */
public record ReferenceCreationRequested(RelationEventMetadata metadata, ReferenceEndpoint source,
                                         ReferenceEndpoint target) {

    public ReferenceCreationRequested {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (source.workingSetId() == null || source.localId() == null || source.revision() == null) {
            throw new IllegalArgumentException("reference source endpoint must be fully resolved");
        }
    }
}
