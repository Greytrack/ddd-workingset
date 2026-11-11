package alm.requirement.application;

import java.util.Objects;

import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.Version;

/**
 * ALM Requirement 应用服务（S1 仅创建；修改与事件发布在 S2 实现）。
 */
public class RequirementApplicationService {

    private final RequirementRepository repository;

    public RequirementApplicationService(RequirementRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** 创建并提交一个 Requirement；并发版本从 0 开始。 */
    public Requirement create(LocalId localId,
                              CommonId commonId,
                              TrackerId trackerId,
                              String title,
                              String description,
                              Revision revision) {
        Requirement requirement = Requirement.create(
                localId, commonId, trackerId, title, description, revision, Version.of(0));
        repository.save(requirement);
        return requirement;
    }
}
