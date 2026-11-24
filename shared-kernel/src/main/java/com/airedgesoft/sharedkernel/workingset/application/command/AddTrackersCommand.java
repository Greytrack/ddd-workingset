package com.airedgesoft.sharedkernel.workingset.application.command;

import java.util.List;

/**
 * 添加 Tracker 至工作集命令（候选树经 intent=WORKINGSET_EXTEND 投影；产品空间随分支携带，创建期挂载走新建向导）。
 *
 * @param branches
 *            待添加分支清单（已存在分支幂等跳过）
 */
public record AddTrackersCommand(List<Branch> branches) {

    /** 工作集分支：产品空间 × Tracker × 分支类型。 */
    public record Branch(String productSpaceId, String trackerId, String branchType) {
    }
}
