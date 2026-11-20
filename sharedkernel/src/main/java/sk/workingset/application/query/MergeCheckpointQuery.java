package sk.workingset.application.query;

import java.util.Optional;

public interface MergeCheckpointQuery {
    Optional<MergeCheckpoint> findLatest(MergeCheckpoint.Key key);
}
