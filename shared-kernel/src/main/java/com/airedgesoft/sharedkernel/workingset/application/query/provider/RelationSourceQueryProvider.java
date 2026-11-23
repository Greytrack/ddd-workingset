package com.airedgesoft.sharedkernel.workingset.application.query.provider;

import com.airedgesoft.sharedkernel.workingset.domain.event.AssociationEndpointEvent;
import com.airedgesoft.sharedkernel.workingset.domain.event.ReferenceEndpointEvent;

import java.util.List;
import java.util.Objects;

/**
 * 业务 BC 向工作集提供上游关系事实。WorkingSet 不拥有关系聚合；本端口只读。
 *
 * <p>{@code originalRelationId} 必须来自业务关系聚合，供消费方回查传播怀疑、关系类型和方向。
 */
public interface RelationSourceQueryProvider {

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    List<ReferenceFact> upstreamReferences(ReferenceEndpointEvent source);

    List<AssociationFact> upstreamAssociations(AssociationEndpointEvent source);

    /**
     * Merge 后按变化的目标端点定位需要修复的引用。
     */
    List<ReferenceFact> referencesTargeting(ReferenceEndpointEvent target);

    /**
     * Merge 后按变化的目标端点定位需要修复的关联。
     */
    List<AssociationFact> associationsTargeting(AssociationEndpointEvent target);

    record ReferenceFact(String originalRelationId, ReferenceEndpointEvent source, ReferenceEndpointEvent target) {
        public ReferenceFact {
            requireText(originalRelationId, "originalRelationId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }
    }

    record AssociationFact(String originalRelationId, AssociationEndpointEvent source,
                           AssociationEndpointEvent target) {
        public AssociationFact {
            requireText(originalRelationId, "originalRelationId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }
    }
}
