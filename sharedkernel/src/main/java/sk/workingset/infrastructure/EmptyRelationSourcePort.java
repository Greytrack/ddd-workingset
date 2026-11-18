package sk.workingset.infrastructure;

import java.util.List;

import sk.workingset.domain.event.AssociationEndpoint;
import sk.workingset.domain.event.ReferenceEndpoint;
import sk.workingset.application.port.RelationSourcePort;

/**
 * 过渡实现：业务 BC 尚未提供关系事实时为空。接入真实关系事实后替换为 BC Adapter。
 */
public final class EmptyRelationSourcePort implements RelationSourcePort {

    @Override
    public List<ReferenceFact> upstreamReferences(ReferenceEndpoint source) {
        return List.of();
    }

    @Override
    public List<AssociationFact> upstreamAssociations(AssociationEndpoint source) {
        return List.of();
    }

    @Override
    public List<ReferenceFact> referencesTargeting(ReferenceEndpoint target) {
        return List.of();
    }

    @Override
    public List<AssociationFact> associationsTargeting(AssociationEndpoint target) {
        return List.of();
    }
}
