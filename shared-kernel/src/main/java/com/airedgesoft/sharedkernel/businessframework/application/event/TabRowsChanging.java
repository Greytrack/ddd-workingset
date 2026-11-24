package com.airedgesoft.sharedkernel.businessframework.application.event;

import java.util.Set;

/**
 * 在关系写入前、同一可写事务内同步发布。发送方提供所有页签行集将发生变化的实例， 包括换绑前后的反向页签所属实例（如果存在）。
 */
public record TabRowsChanging(Set<AffectedInstance> affectedInstances) {

    public TabRowsChanging {
        affectedInstances = Set.copyOf(affectedInstances);
    }

    public record AffectedInstance(long baseClassId, long instanceId) {
        public AffectedInstance {
            if (baseClassId <= 0 || instanceId <= 0) {
                throw new IllegalArgumentException("Tab owner must have a positive base class ID and instance ID");
            }
        }
    }
}
