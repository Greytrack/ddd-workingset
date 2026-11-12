package bootstrap.web;

/** 创建 Requirement 请求；localId 由服务端雪花生成器分配。 */
public record CreateRequirementRequest(String commonId,
                                       String trackerId,
                                       String title,
                                       String description,
                                       int revision) {
}
