package sk.workingset.domain.merge;

import java.util.Optional;

import org.jmolecules.ddd.annotation.Repository;

/** MergeCheckpoint 独立聚合仓储；确认/撤销事实由聚合行为产生后通过 save 持久化。 */
@Repository
public interface MergeCheckpointRepository {
    void save(MergeCheckpoint checkpoint);

    Optional<MergeCheckpoint> findById(MergeCheckpoint.Key key);
}
