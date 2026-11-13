package sk.workingset.application.port;

import java.util.Optional;

import sk.workingset.domain.WorkingSetCreation;
import sk.workingset.domain.WorkingSetId;

public interface WorkingSetCreationStore {
    boolean insert(WorkingSetCreation creation);
    Optional<WorkingSetCreation> find(String id, boolean lock);
    Optional<WorkingSetCreation> findByTarget(WorkingSetId target);
    void update(WorkingSetCreation creation);
}
