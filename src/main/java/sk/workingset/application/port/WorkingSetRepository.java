package sk.workingset.application.port;

import java.util.List;
import java.util.Optional;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;

/** WorkingSet 聚合存取、绑定关系与谱系查询（子级/祖先）。 */
public interface WorkingSetRepository {
    Optional<WorkingSet> findWorkingSet(WorkingSetId id);
    Optional<WorkingSet> lockWorkingSet(WorkingSetId id);
    boolean insertWorkingSet(WorkingSet workingSet);
    List<WorkingSetId> findChildren(WorkingSetId id);
    List<WorkingSetId> findAncestors(WorkingSetId id);
    void insertSpaceBinding(WorkingSet.SpaceBinding binding);
    void insertTrackerBinding(WorkingSet.TrackerBinding binding);
    void deleteTrackerBinding(WorkingSetId id, TrackerId trackerId);
    void deleteBindingsToTracker(TrackerId trackerId);
    void deleteSpaceBinding(WorkingSetId id, String spaceId);
    List<WorkingSetId> findWorkingSetsBoundToSpace(String spaceId);
    void deleteWorkingSet(WorkingSetId id);
}
