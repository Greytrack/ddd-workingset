package sk.workingset.application.port;

/**
 * 业务对象类型：Tracker 的 {@code tracker_class}，也就是聚合里的 {@code classificationId}。
 *
 * <p>{@code type = tracker_class = subClassId = classificationId}，是分类/子类雪花 ID。
 * 一个 Tracker 只承载一种类型，路由以该 ID 精确匹配；SK 不解释分类的业务含义。
 */
public record BusinessObjectType(long value) {

    public BusinessObjectType {
        if (value <= 0) {
            throw new IllegalArgumentException("business object type must be positive");
        }
    }
}
