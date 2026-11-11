package alm.requirement.infrastructure;

import java.util.Optional;

import sk.workingset.application.event.SnapshotRef;

/**
 * ALM 自有快照存储：保存「修改前」不可变内容并返回引用；SK 只持有引用。
 */
public interface SnapshotStore {

    /** 保存快照并返回不可变引用。 */
    SnapshotRef save(RequirementSnapshot snapshot);

    /** 按引用回读快照；不存在时返回空，不以当前值冒充。 */
    Optional<RequirementSnapshot> find(SnapshotRef ref);
}
