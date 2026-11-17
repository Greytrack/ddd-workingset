package alm.requirement.infrastructure;

import java.util.Objects;

import alm.requirement.domain.Requirement;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.Version;

/**
 * ALM 自有的不可变内容快照（P4）：捕获「修改前」的完整字段，不由 SK 解释。
 *
 * <p>字段集与首切片 P5 一致：身份、标题、描述、业务版次、发布大版本与乐观锁。
 */
public record RequirementSnapshot(LocalId localId,
                                  CommonId commonId,
                                  TrackerId trackerId,
                                  String title,
                                  String description,
                                  Revision revision,
                                  Version version,
                                  LockVersion lockVersion) {

    public RequirementSnapshot {
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(commonId, "commonId");
        Objects.requireNonNull(trackerId, "trackerId");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(lockVersion, "lockVersion");
    }

    /** 在业务修改生效前，从当前聚合捕获完整内容。 */
    public static RequirementSnapshot of(Requirement requirement) {
        Objects.requireNonNull(requirement, "requirement");
        return new RequirementSnapshot(
                requirement.localId(),
                requirement.commonId(),
                requirement.trackerId(),
                requirement.title(),
                requirement.description(),
                requirement.revision(),
                requirement.version(),
                requirement.lockVersion());
    }
}
