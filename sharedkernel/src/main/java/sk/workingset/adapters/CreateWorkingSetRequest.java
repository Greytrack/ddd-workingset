package sk.workingset.adapters;

import sk.workingset.domain.workingset.WorkingSetCreation;

import java.util.List;
import java.util.Map;

public record CreateWorkingSetRequest(String parentWorkingSetId, String requestId,
                                      List<String> productSpaceIds, List<String> sharedTrackerIds,
                                      List<String> unsharedParentTrackerIds, String name,
                                      WorkingSetCreation.Mode mode, String color,
                                      Map<Long, Long> baselineAtByTracker) {
    WorkingSetCreation.Request toDomainRequest(String newWorkingSetId) {
        WorkingSetCreation.Mode creationMode = mode == null ? WorkingSetCreation.Mode.CREATE : mode;
        return new WorkingSetCreation.Request(parentWorkingSetId, newWorkingSetId, requestId, productSpaceIds,
                sharedTrackerIds, unsharedParentTrackerIds, name, creationMode, color, baselineAtByTracker);
    }
}
