package com.airedgesoft.alm.requirement.application;



import java.util.Set;

/**
 * 由 Requirement 业务能力支持的 Tracker 分类。
 */
public final class RequirementTypes {
    public static final Set<Long> SUPPORTED = Set.of(
            14010101L, 14010102L);

    private RequirementTypes() {
    }
}
