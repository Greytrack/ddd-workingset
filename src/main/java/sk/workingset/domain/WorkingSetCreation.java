package sk.workingset.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/** Durable execution of one request to create and initialize a child working set. */
public record WorkingSetCreation(String id, Request request, String requestedBy, Status status,
                                 Stage stage, Progress progress, List<Failure> failures,
                                 Instant createdAt, Instant startedAt, Instant completedAt,
                                 Map<Stage, Progress> stageProgress) {
    public enum Status { Pending, Running, Succeeded, PartiallySucceeded, Failed }
    public enum Stage {
        CREATE_WORKING_SET, BIND_PRODUCT_SPACES, COPY_UNSHARED_TRACKERS, BIND_UNSHARED_TRACKERS, BIND_SHARED_TRACKERS
    }

    public enum ItemOutcome { Succeeded, Failed, Skipped }

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

        public List<String> items(Stage stage) {
            return switch (stage) {
                case CREATE_WORKING_SET -> List.of(newWorkingSetId);
                case BIND_PRODUCT_SPACES -> productSpaceIds;
                case COPY_UNSHARED_TRACKERS, BIND_UNSHARED_TRACKERS -> unsharedParentTrackerIds;
                case BIND_SHARED_TRACKERS -> sharedTrackerIds;
            };
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

    public record Progress(int succeeded, int failed, int skipped, Integer total) {
        int processed() {
            return succeeded + failed + skipped;
        }

        Progress record(ItemOutcome outcome) {
            return switch (outcome) {
                case Succeeded -> new Progress(succeeded + 1, failed, skipped, total);
                case Failed -> new Progress(succeeded, failed + 1, skipped, total);
                case Skipped -> new Progress(succeeded, failed, skipped + 1, total);
            };
        }
    }
    public record Failure(Stage stage, String productSpaceId, String trackerId, String localId,
                          String reason, Instant occurredAt) {}

    public WorkingSetCreation {
        failures = List.copyOf(failures);
        stageProgress = stageProgress == null ? Map.of(Stage.CREATE_WORKING_SET, progress) : Map.copyOf(stageProgress);
    }

    public static WorkingSetCreation submit(String id, Request request, String caller, Instant now) {
        int total = 0;
        for (Stage stage : Stage.values()) {
            total += request.items(stage).size();
        }
        return new WorkingSetCreation(id, Objects.requireNonNull(request), caller, Status.Pending,
                Stage.CREATE_WORKING_SET, new Progress(0, 0, 0, total), List.of(), now, null, null,
                Map.of(Stage.CREATE_WORKING_SET, new Progress(0, 0, 0, 1)));
    }

    public WorkingSetCreation start(Instant now) {
        if (status != Status.Pending) {
            throw new IllegalStateException("Only a pending creation can start");
        }
        return new WorkingSetCreation(id, request, requestedBy, Status.Running,
                stage, progress, failures, createdAt, now, null, stageProgress);
    }

    public String currentItem() {
        requireRunning();
        return request.items(stage).get(stageProgress.get(stage).processed());
    }

    public WorkingSetCreation advance(ItemOutcome outcome, Failure failure, Instant now) {
        requireRunning();
        if ((outcome == ItemOutcome.Failed) != (failure != null) || failure != null && failure.stage() != stage) {
            throw new IllegalArgumentException("Failure detail must match the current step and outcome");
        }
        var updatedProgress = new EnumMap<Stage, Progress>(Stage.class);
        updatedProgress.putAll(stageProgress);
        var current = updatedProgress.get(stage).record(outcome);
        updatedProgress.put(stage, current);
        var updatedFailures = new ArrayList<>(failures);
        if (failure != null) {
            updatedFailures.add(failure);
        }
        var totals = progress.record(outcome);
        var next = stage;
        var nextStatus = Status.Running;
        if (stage == Stage.CREATE_WORKING_SET && outcome == ItemOutcome.Failed) {
            nextStatus = Status.Failed;
        } else if (current.processed() == current.total()) {
            Stage[] stages = Stage.values();
            int index = stage.ordinal() + 1;
            while (index < stages.length && request.items(stages[index]).isEmpty()) {
                updatedProgress.put(stages[index++], new Progress(0, 0, 0, 0));
            }
            if (index == stages.length) {
                nextStatus = updatedFailures.isEmpty() ? Status.Succeeded : Status.PartiallySucceeded;
            } else {
                next = stages[index];
                updatedProgress.put(next, new Progress(0, 0, 0, request.items(next).size()));
            }
        }
        return new WorkingSetCreation(id, request, requestedBy, nextStatus, next, totals, updatedFailures,
                createdAt, startedAt, nextStatus == Status.Running ? null : now, updatedProgress);
    }

    private void requireRunning() {
        if (status != Status.Running) {
            throw new IllegalStateException("Only a running creation can finish");
        }
    }
}
