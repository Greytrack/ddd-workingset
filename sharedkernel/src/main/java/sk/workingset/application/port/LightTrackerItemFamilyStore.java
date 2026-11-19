package sk.workingset.application.port;

import sk.workingset.domain.lighttrackeritem.LightTrackerItem;

/**
 * LightTrackerItem family 的唯一性认领。
 *
 * <p>这是索引流程的原子认领机制，不是聚合仓储；聚合持久化仍由
 * {@code LightTrackerItemRepository.save} 负责。
 */
public interface LightTrackerItemFamilyStore {
    boolean claim(LightTrackerItem item);
}
