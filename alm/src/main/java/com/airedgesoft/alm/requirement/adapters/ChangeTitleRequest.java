package com.airedgesoft.alm.requirement.adapters;

/**
 * 普通修改标题请求；expectedLockVersion 用于乐观锁校验。
 */
public record ChangeTitleRequest(String title, long expectedLockVersion) {
    public ChangeTitleRequest {
        if (expectedLockVersion < 0) {
            throw new IllegalArgumentException("lockVersion must not be negative");
        }
    }
}
