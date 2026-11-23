package com.airedgesoft.sharedkernel.workingset.domain.event;

import com.fasterxml.jackson.annotation.JsonFormat;



/**
 * Merge 业务写入完成后的目标变体事实（D15-B）。
 *
 * @param localId      目标业务对象身份
 * @param commonId     家族身份
 * @param trackerId    目标 Tracker
 * @param workingSetId 目标工作集（`round.target()`）
 * @param revision     业务写入后的版本
 * @param inherited    目标原本是继承变体，需要切换为本地物化
 * @param create       需要在目标工作集新建本地 LightTrackerItem
 */
public record MergeTargetEvent(@JsonFormat(shape = JsonFormat.Shape.STRING) Long localId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long commonId, @JsonFormat(shape = JsonFormat.Shape.STRING) Long trackerId,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) Long workingSetId, Integer revision,
                               boolean inherited, boolean create) {
}
