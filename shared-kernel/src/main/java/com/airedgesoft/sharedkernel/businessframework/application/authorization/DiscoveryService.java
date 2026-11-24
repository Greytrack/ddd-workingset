package com.airedgesoft.sharedkernel.businessframework.application.authorization;

import java.util.Collection;
import java.util.List;

/**
 * 发现权限共享应用服务端口（方案 A 上提至 shared-kernel，由 {@code v4-identityaccess} 实现）。
 */
public interface DiscoveryService {

    /**
     * Builds an authorization condition for the persistence query, without a leading WHERE or AND. The alias must be a
     * trusted alias of the resource view; bind parameters in the returned order.
     */
    RenderedSql buildPredicate(Long userId, int privilege, Collection<Long> requestedClassIds,
            PredicateAssembly assembly, String resourceAlias);

    /** Resolves the IDs of the objects that are visible to the user. */
    List<Long> resolveVisibleIds(Long userId, int privilege, Collection<Long> classIds);
}
