package com.airedgesoft.sharedkernel.workingset.application.query.model;

/**
 * ClassSnapshot 读模型。
 */
public record ClassificationSummary(Long classificationId, String sharingMode, String name,
                                    Long baseClassId, Long classId) {
}
