package com.airedgesoft.sharekernel.workingset.infrastructure.providers;

import com.airedgesoft.sharekernel.workingset.application.query.provider.RelationSourceQueryProvider;
import com.airedgesoft.sharekernel.workingset.domain.event.AssociationEndpointEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.ReferenceEndpointEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
