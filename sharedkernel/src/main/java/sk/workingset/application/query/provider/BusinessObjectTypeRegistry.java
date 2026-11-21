package sk.workingset.application.query.provider;

import sk.tracker.domain.TrackerId;
import sk.workingset.application.port.BusinessObjectTypeAdapter;
import sk.workingset.domain.LocalId;

/**
 * SK 内唯一的“业务对象类型 → Adapter”路由点。
 *
 * <p>路由以 Tracker 为第一键；只有 LocalId 时先解析所属 LightTrackerItem，再回到 Tracker。
 * 未注册的类型返回 {@code CapabilityNotSupported}，不得静默当作“没有 Item”。
 */
public interface BusinessObjectTypeRegistry {

    BusinessObjectTypeAdapter forTracker(TrackerId trackerId);

    BusinessObjectTypeAdapter forItem(LocalId localId);
}
