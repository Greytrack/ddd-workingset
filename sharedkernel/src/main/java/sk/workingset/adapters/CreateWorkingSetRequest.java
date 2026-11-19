package sk.workingset.adapters;

import sk.workingset.domain.WorkingSetCreation;

import java.util.List;

public record CreateWorkingSetRequest(String parentWorkingSetId, String requestId,
                                      List<String> productSpaceIds, List<String> sharedTrackerIds,
                                      List<String> unsharedParentTrackerIds, String name,
                                      WorkingSetCreation.Mode mode) {
    WorkingSetCreation.Request toDomainRequest(String newWorkingSetId) {
        return new WorkingSetCreation.Request(parentWorkingSetId, newWorkingSetId, requestId, productSpaceIds,
                sharedTrackerIds, unsharedParentTrackerIds, name, mode);
    }
}
