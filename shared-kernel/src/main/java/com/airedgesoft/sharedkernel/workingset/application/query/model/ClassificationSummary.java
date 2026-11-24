package com.airedgesoft.sharedkernel.workingset.application.query.model;

/**
 * Classification 读模型。
 */
public record ClassificationSummary(Long classificationId, boolean shared, String name,
                                    Long baseClassId, Long classId) {
}
