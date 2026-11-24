package com.airedgesoft.sharedkernel.workingset.application.query.model;

/**
 * TrackerBinding 读模型。
 */
public record TrackerBindingSummary(Long trackerId, Long productSpaceId, Long classificationId,
                                    boolean shared, String origin, boolean shown) {
}
