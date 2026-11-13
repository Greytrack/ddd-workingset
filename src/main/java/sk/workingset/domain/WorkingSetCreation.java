package sk.workingset.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Durable execution of one request to create and initialize a child working set. */
public record WorkingSetCreation(String id, Request request, String requestedBy, Status status,
                                 Stage stage, Progress progress, List<Failure> failures,
                                 Instant createdAt, Instant startedAt, Instant completedAt) {
    public enum Status { Pending, Running, Succeeded, PartiallySucceeded, Failed }
    public enum Stage { CREATE_WORKING_SET }

    public record Request(String parentWorkingSetId, String newWorkingSetId, String requestId,
                          List<String> productSpaceIds, List<String> sharedTrackerIds,
                          List<String> unsharedParentTrackerIds) {
        public Request {
            validateId(parentWorkingSetId, "parentWorkingSetId");
            validateId(newWorkingSetId, "newWorkingSetId");
            validateId(requestId, "requestId");
            productSpaceIds = normalize(productSpaceIds);
            sharedTrackerIds = normalize(sharedTrackerIds);
            unsharedParentTrackerIds = normalize(unsharedParentTrackerIds);
        }

        public boolean hasSelection() {
            return !productSpaceIds.isEmpty() || !sharedTrackerIds.isEmpty() || !unsharedParentTrackerIds.isEmpty();
        }

        private static List<String> normalize(List<String> ids) {
            var normalized = new TreeSet<String>();
            if (ids != null) {
                for (String id : ids) {
                    validateId(id, "selection ID");
                    normalized.add(id);
                }
            }
            return List.copyOf(normalized);
        }

        private static void validateId(String id, String field) {
            if (id == null || id.isBlank() || id.length() > 128 || id.indexOf(0) >= 0) {
                throw new IllegalArgumentException(field + " must contain 1 to 128 characters");
            }
        }
    }

    public record Progress(int succeeded, int failed, int skipped, Integer total) {}
    public record Failure(Stage stage, String productSpaceId, String trackerId, String localId,
                          String reason, Instant occurredAt) {}

    public WorkingSetCreation {
        failures = List.copyOf(failures);
    }

    public static WorkingSetCreation submit(String id, Request request, String caller, Instant now) {
        return new WorkingSetCreation(id, Objects.requireNonNull(request), caller, Status.Pending,
                Stage.CREATE_WORKING_SET, new Progress(0, 0, 0, 1), List.of(), now, null, null);
    }

    public WorkingSetCreation start(Instant now) {
        if (status != Status.Pending) {
            throw new IllegalStateException("Only a pending creation can start");
        }
        return new WorkingSetCreation(id, request, requestedBy, Status.Running,
                stage, progress, failures, createdAt, now, null);
    }

    public WorkingSetCreation completeBody(Instant now) {
        requireRunning();
        return new WorkingSetCreation(id, request, requestedBy, Status.Succeeded,
                stage, new Progress(1, 0, 0, 1), List.of(), createdAt, startedAt, now);
    }

    public WorkingSetCreation failBody(String reason, Instant now) {
        requireRunning();
        return new WorkingSetCreation(id, request, requestedBy, Status.Failed, stage,
                new Progress(0, 1, 0, 1), List.of(new Failure(stage, null, null, null, reason, now)),
                createdAt, startedAt, now);
    }

    private void requireRunning() {
        if (status != Status.Running) {
            throw new IllegalStateException("Only a running creation can finish");
        }
    }
}
