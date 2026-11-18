package alm.requirement.domain;

import java.util.Optional;

import alm.requirement.application.command.RequirementSnapshot;
import sk.workingset.domain.event.SnapshotRefEvent;

/**
 * ALM 自有快照存储：保存「修改前」不可变内容并返回引用；SK 只持有引用。
 */
public interface SnapshotRepository {

    /** 保存快照并返回不可变引用。 */
    SnapshotRefEvent save(RequirementSnapshot snapshot);

    /** 按引用回读快照；不存在时返回空，不以当前值冒充。 */
    Optional<RequirementSnapshot> find(SnapshotRefEvent ref);
}
