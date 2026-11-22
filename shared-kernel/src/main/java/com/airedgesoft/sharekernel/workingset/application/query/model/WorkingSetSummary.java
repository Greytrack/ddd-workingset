package com.airedgesoft.sharekernel.workingset.application.query.model;

/**
 * Working Set 查询摘要；ID 保持数值类型，由 Controller 决定 JSON 表示。
 */
public record WorkingSetSummary(Long workingSetId, Long parentWorkingSetId, String name,
                                boolean defaultWorkingSet, String color) {
}
