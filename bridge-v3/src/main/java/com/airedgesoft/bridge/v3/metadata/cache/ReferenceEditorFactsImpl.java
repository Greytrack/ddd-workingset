package com.airedgesoft.bridge.v3.metadata.cache;

import com.airedgesoft.sharedkernel.businessframework.application.query.ReferenceEditorFacts;
import com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceGuardDomainService;
import org.springframework.stereotype.Component;

@Component
public class ReferenceEditorFactsImpl implements ReferenceEditorFacts {
    @Override
    public ReferenceGuardDomainService.EditorFacts loadEditorFacts(Long sourceId) {
        return null;
    }
}
