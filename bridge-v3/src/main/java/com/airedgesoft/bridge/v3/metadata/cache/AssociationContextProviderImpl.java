package com.airedgesoft.bridge.v3.metadata.cache;

import com.airedgesoft.sharedkernel.businessframework.domain.association.Association;
import com.airedgesoft.sharedkernel.businessframework.infrastructure.association.AssociationContextProvider;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

@Component
public class AssociationContextProviderImpl implements AssociationContextProvider {
    @Override
    public AssociationContext load(Long sourceId, Long targetId) {
        return null;
    }

    @Override
    public Map<Long, AssociationContext> loadAll(Collection<Association> rows) {
        return Map.of();
    }
}
