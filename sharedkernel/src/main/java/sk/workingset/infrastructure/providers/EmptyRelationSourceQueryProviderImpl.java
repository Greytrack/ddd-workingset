package sk.workingset.infrastructure.providers;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import sk.workingset.application.query.provider.RelationSourceQueryProvider;
import sk.workingset.domain.event.AssociationEndpointEvent;
import sk.workingset.domain.event.ReferenceEndpointEvent;

import java.util.List;

/**
 * 过渡实现：业务 BC 尚未提供关系事实时为空。接入真实关系事实后替换为 BC Adapter。
 */
@Component
@RequiredArgsConstructor
public class EmptyRelationSourceQueryProviderImpl implements RelationSourceQueryProvider {

    @Override
    public List<ReferenceFact> upstreamReferences(ReferenceEndpointEvent source) {
        return List.of();
    }

    @Override
    public List<AssociationFact> upstreamAssociations(AssociationEndpointEvent source) {
        return List.of();
    }

    @Override
    public List<ReferenceFact> referencesTargeting(ReferenceEndpointEvent target) {
        return List.of();
    }

    @Override
    public List<AssociationFact> associationsTargeting(AssociationEndpointEvent target) {
        return List.of();
    }
}
