package com.airedgesoft.sharedkernel.businessframework.application.query;

import com.airedgesoft.sharedkernel.businessframework.domain.reference.ReferenceType;

/**
 * 引用类型装配端口（裁决 4：目标 Tracker 共享→DIRECT，否则 RELATIVE）。
 *
 * <p>
 * 共享性事实经 Tracker 语境现表直查（{@code air_ws_tracker.is_shared}），不装配整个 Tracker 聚合（§13 跨 BC 只按 ID 引用）； 引用侧只读 Tracker 的一个布尔位。
 */
public interface ReferenceTypeResolver {

    /**
     * 按目标装配引用类型。
     *
     * @param targetId
     *            目标对象 id
     * @return 引用类型
     */
    ReferenceType resolve(Long targetId);
}
