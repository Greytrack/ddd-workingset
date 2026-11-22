package com.airedgesoft.sharekernel.workingset.application.query.model;

/**
 * Tracker 列表读模型。
 */
public record TrackerSummary(Long trackerId, String name, Long parentId, Long sourceId,
                             Long productSpaceId, Long classificationId, String sharingMode,
                             boolean isDefault) {
}
