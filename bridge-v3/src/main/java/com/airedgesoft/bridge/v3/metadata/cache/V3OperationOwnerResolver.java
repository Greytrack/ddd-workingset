package com.airedgesoft.bridge.v3.metadata.cache;

import com.airedgesoft.lib.restful.lro.OperationOwnerResolver;
import com.airedgesoft.sharedkernel.businessframework.application.context.AuthorizationScope;
import org.springframework.stereotype.Component;

@Component
public class V3OperationOwnerResolver implements OperationOwnerResolver {
    @Override
    public String currentOwner() {
        return AuthorizationScope.currentUserId().toString();
    }
}
