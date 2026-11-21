package sk.workingset.domain.lighttrackeritem;

/**
 * LightTrackerItem 带入的分类与空间事实。
 *
 * <p>映射到索引表的 {@code sub_class_id}（classificationId）和 {@code work_space_id}
 * （numericProductSpaceId）。来源是所属 Tracker，而不是业务对象的公共摘要。
 */
public record ItemClassification(long numericProductSpaceId, long classificationId) {

    public ItemClassification {
        if (numericProductSpaceId <= 0 || classificationId <= 0) {
            throw new IllegalArgumentException("product space and classification IDs must be positive");
        }
    }
}
