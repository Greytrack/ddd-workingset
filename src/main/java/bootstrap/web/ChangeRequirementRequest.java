package bootstrap.web;

/** 编辑单个需求的标题和描述；expectedLockVersion 用于乐观锁校验。 */
public record ChangeRequirementRequest(String title, String description, long expectedLockVersion) {
}
