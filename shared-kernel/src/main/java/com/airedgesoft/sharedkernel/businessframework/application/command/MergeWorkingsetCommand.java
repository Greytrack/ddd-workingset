package com.airedgesoft.sharedkernel.businessframework.application.command;

import java.util.List;

/**
 * 工作集合并命令（把源工作集的分支内容并入当前工作集；双向修改冲突 409+明细；同步执行）。
 *
 * @param sourceWorkingsetId
 *            源工作集 id（必填）
 * @param spaceId
 *            限定合并的产品空间 id（可选；缺省合并全部）
 * @param subclassId
 *            限定的分类 id（可选）
 * @param itemIds
 *            限定合并的需求 id 集合（可选；缺省按范围全量）
 */
public record MergeWorkingsetCommand(String sourceWorkingsetId, String spaceId, String subclassId,
        List<String> itemIds) {
}
