package sk.workingset.domain.event;

import java.util.Objects;

/** 请求关系服务修复已有 Reference；{@code originalRelationId} 必须指向原关系聚合。 */
public record DanglingReferenceRepairRequested(RelationEventMetadata metadata, ReferenceEndpoint source,
                                               ReferenceEndpoint target) {

    public DanglingReferenceRepairRequested {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        if (source.workingSetId() == null || source.localId() == null || source.revision() == null) {
            throw new IllegalArgumentException("reference source endpoint must be fully resolved");
        }
    }
}
