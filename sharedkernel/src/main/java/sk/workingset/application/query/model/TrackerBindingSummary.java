package sk.workingset.application.query.model;

/**
 * TrackerBinding 读模型。
 */
public record TrackerBindingSummary(Long trackerId, Long productSpaceId, Long classificationId,
                                    String sharingMode, String origin, boolean shown, boolean rootFolder) {
}
