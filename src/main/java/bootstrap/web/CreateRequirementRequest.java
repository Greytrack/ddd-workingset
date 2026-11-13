package bootstrap.web;

/**
 * 创建 Requirement 请求；身份由服务端分配（D T6-C）。
 *
 * <p>{@code workingSetId} 是可选的工作集入口：为空时按 Tracker 归属决定；
 * 默认工作集生成同一个雪花 ID 作为 CommonId 与 LocalId，非默认工作集生成两个不等的 ID。
 */
public record CreateRequirementRequest(String workingSetId,
                                       String trackerId,
                                       String title,
                                       String description,
                                       int revision) {
}
