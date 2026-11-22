package com.airedgesoft.alm.requirement.application.query.provider;

import com.airedgesoft.alm.requirement.application.query.model.RequirementDetail;

import java.util.List;
import java.util.Optional;

/**
 * Requirement 查询侧只读端口。
 */
public interface RequirementQueryProvider {
    Optional<RequirementDetail> find(long localId);

    /**
     * 共享 Tracker 的 Item 属于其权威工作集，但通过绑定对所有子工作集可见；
     * 非共享 Tracker 的 Item 属于单一工作集。返回包含软删状态的本地行，供应用侧决定是否展示。
     */
    List<RequirementDetail> findByTracker(long trackerId);

    List<RequirementDetail> findByIds(List<Long> localIds);
}
