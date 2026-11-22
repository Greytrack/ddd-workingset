package com.airedgesoft.alm.requirement.domain;


import java.util.Optional;

/**
 * Requirement 聚合的命令侧存取（ALM 自有 Repository；SK 不得直接访问，A1）。
 */
public interface RequirementRepository {

    /**
     * 保存聚合（新增或更新）。
     */
    void save(Requirement requirement);

    /**
     * 按 LocalId 重建聚合。
     */
    Optional<Requirement> findById(Long localId);
}
