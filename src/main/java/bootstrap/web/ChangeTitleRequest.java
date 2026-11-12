package bootstrap.web;

/** 普通修改标题请求；expectedLockVersion 用于乐观锁校验。 */
public record ChangeTitleRequest(String title, long expectedLockVersion) {
}
