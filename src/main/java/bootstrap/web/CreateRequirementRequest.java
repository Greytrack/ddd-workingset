package bootstrap.web;

/**
 * 创建 Requirement 请求；localId 由服务端雪花生成器分配。
 *
 * <p>{@code workingSetId} 是 D10 的工作集入口：共享 Tracker 可来自任意绑定工作集，
 * 实际归属默认工作集；非共享 Tracker 必须由它所在的工作集创建。为空时默认工作集。
 */
public record CreateRequirementRequest(String workingSetId,
                                       String commonId,
                                       String trackerId,
                                       String title,
                                       String description,
                                       int revision) {
}
