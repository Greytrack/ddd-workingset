package sk.workingset.application.query.model;

/**
 * Classification 读模型。
 */
public record ClassificationSummary(Long classificationId, String sharingMode, String name,
                                    Long baseClassId, Long classId) {
}
