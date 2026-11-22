package com.airedgesoft.alm.requirement.adapters;

/**
 * 编辑单个需求的标题和描述；expectedLockVersion 用于乐观锁校验。
 */
public record ChangeRequirementRequest(String title, String description, long expectedLockVersion) {
    public ChangeRequirementRequest {
        if (expectedLockVersion < 0) {
            throw new IllegalArgumentException("lockVersion must not be negative");
        }
    }
}
