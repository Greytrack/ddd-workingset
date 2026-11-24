package com.airedgesoft.sharedkernel.businessframework.application.event;

import java.util.Set;

/**
 * 在对象关联关系写入前、同一可写事务内同步发布。发送方提供所有关系投影将发生变化的实例， 换绑时应包含变更前和变更后受影响的实例。
 */
public record AssociationChange(Set<AffectedInstance> affectedInstances) {

    public AssociationChange {
        affectedInstances = Set.copyOf(affectedInstances);
    }

    public record AffectedInstance(long baseClassId, long instanceId) {
        public AffectedInstance {
            if (baseClassId <= 0 || instanceId <= 0) {
                throw new IllegalArgumentException(
                        "Affected instance must have a positive base class ID and instance ID");
            }
        }
    }
}
