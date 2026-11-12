package sk.workingset.application.port;

import java.util.Optional;

import sk.workingset.domain.merge.Comparison;
import sk.workingset.domain.merge.MergeCheckpoint;
import sk.workingset.domain.merge.MergeRound;

public interface MergeStore {
    void insertComparison(Comparison comparison);
    Optional<Comparison> findComparison(String token);

    boolean insertRound(MergeRound round);
    Optional<MergeRound> findRound(String id, boolean lock);
    void updateRound(MergeRound round);

    long nextDecisionVersion();
    boolean recordCheckpoint(MergeCheckpoint checkpoint);
    Optional<MergeCheckpoint> findCheckpoint(MergeCheckpoint.Key key);
    boolean revokeCheckpoint(MergeCheckpoint.Key key, long expectedVersion, long newVersion);
}
