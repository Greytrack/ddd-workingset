package sk.workingset.domain.merge;

import java.util.Optional;

import org.jmolecules.ddd.annotation.Repository;

@Repository
public interface MergeCheckpointRepository {
    boolean save(MergeCheckpoint checkpoint);

    Optional<MergeCheckpoint> findCheckpoint(MergeCheckpoint.Key key);

    boolean revokeCheckpoint(MergeCheckpoint.Key key, long expectedVersion, long newVersion);

    long nextDecisionVersion();

    int deleteSupersededCheckpoints();
}
