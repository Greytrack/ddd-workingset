package com.airedgesoft.alm.requirement.application.port;

import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.alm.requirement.domain.RequirementRepository;

import java.util.Optional;

/**
 * Requirement 写入用例所需的聚合仓储与事务内锁定能力。
 */
public interface RequirementMutationStore extends RequirementRepository {
    Optional<Requirement> lockById(Long id);

    Optional<Requirement> lockFamilyInWorkingSet(Long trackerId, Long commonId);

    void purge(Requirement requirement);
}
