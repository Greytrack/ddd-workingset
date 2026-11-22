package com.airedgesoft.sharekernel.workingset.application.query.provider;


import java.util.List;
import java.util.Optional;

/**
 * LightTrackerItem 的只读查询；列表、分页和存在性判断不进入聚合仓储。
 */
public interface LightTrackerItemQueryProvider {

    /**
     * 按 LocalId 读取摘要；包含软删行，供生命周期同步与类型路由使用。
     */
    Optional<LightTrackerItemSummary> findById(Long localId);

    List<LightTrackerItemSummary> findInTracker(Long trackerId);

    List<LightTrackerItemSummary> pageInTracker(Long trackerId, String afterLocalId, int limit);

    List<LightTrackerItemSummary> pageInTracker(Long trackerId, long baselineAt, String afterLocalId, int limit);

    boolean existsInTracker(Long trackerId);

    boolean hasFamily(Long workingSetId, Long commonId);

    boolean hasSourceBackedDependents(Long localId);
}
