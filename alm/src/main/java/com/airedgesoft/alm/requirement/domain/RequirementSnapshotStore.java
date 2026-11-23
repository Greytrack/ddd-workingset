package com.airedgesoft.alm.requirement.domain;

import com.airedgesoft.alm.requirement.application.command.RequirementSnapshot;
import com.airedgesoft.sharedkernel.workingset.domain.event.SnapshotRefEvent;

import java.util.Optional;

/**
 * 读取来源变更事件中由 ALM 定义的不可变快照。
 */
public interface RequirementSnapshotStore {

    /**
     * 按引用回读快照；不存在时返回空，不以当前值冒充。
     */
    Optional<RequirementSnapshot> find(SnapshotRefEvent ref);
}
