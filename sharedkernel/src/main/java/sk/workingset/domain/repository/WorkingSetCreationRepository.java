package sk.workingset.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.workingset.domain.WorkingSetCreation;
import sk.workingset.domain.WorkingSetId;

import java.util.Optional;

@Repository
public interface WorkingSetCreationRepository {
    boolean insert(WorkingSetCreation creation);

    Optional<WorkingSetCreation> find(String id, boolean lock);

    Optional<WorkingSetCreation> findByTarget(WorkingSetId target);

    Optional<WorkingSetCreation> findByRequest(String requestId);

    Optional<WorkingSetCreation> findActiveExtension(WorkingSetId target);

    void update(WorkingSetCreation creation);
}
