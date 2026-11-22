package com.airedgesoft.sharekernel.workingset.application.query.provider;

import com.airedgesoft.sharekernel.workingset.application.port.BusinessObjectTypeAdapter;

/**
 * SK 内唯一的“业务对象类型 → Adapter”路由点。
 *
 * <p>路由以 Tracker 为第一键；只有 LocalId 时先解析所属 LightTrackerItem，再回到 Tracker。
 * 未注册的类型返回 {@code CapabilityNotSupported}，不得静默当作“没有 Item”。
 */
public interface BusinessObjectTypeRegistry {

    BusinessObjectTypeAdapter forTracker(Long trackerId);

    BusinessObjectTypeAdapter forItem(Long localId);
}
