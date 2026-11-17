package alm.requirement.domain;

import java.util.Optional;

import sk.workingset.domain.LocalId;

/**
 * Requirement 聚合的命令侧存取（ALM 自有 Repository；SK 不得直接访问，A1）。
 */
public interface RequirementRepository {

    void save(Requirement requirement);

    Optional<Requirement> findById(LocalId localId);
}
