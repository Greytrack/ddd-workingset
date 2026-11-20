package sk.workingset.domain.event;

import sk.workingset.domain.workingset.WorkingSetId;

import java.util.Objects;

/**
 * Working Set 已建立某产品空间的 SpaceBinding，需要为该工作集创建其文件夹 Tracker。
 */
public record WorkingSetSpaceBoundEvent(WorkingSetId workingSetId, String productSpaceId) {
    public WorkingSetSpaceBoundEvent {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(productSpaceId, "productSpaceId");
    }
}
