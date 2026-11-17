package sk.workingset.domain;

import sk.common.error.DomainException;
import sk.common.error.WorkingsetResultCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Durable execution of a working-set materialization request: create a child or extend an existing child. */
public record WorkingSetCreation(String id, Request request, String requestedBy, Status status,
                                 Stage stage, Progress progress, List<Failure> failures,
                                 Instant createdAt, Instant startedAt, Instant completedAt,
                                 Map<Stage, Progress> stageProgress, Map<String, IndexProgress> indexProgress) {
    public enum Status { Pending, Running, Succeeded, PartiallySucceeded, Failed }
    public enum Mode { CREATE, EXTEND }
    public enum Stage {
        CREATE_WORKING_SET, BIND_PRODUCT_SPACES, COPY_UNSHARED_TRACKERS, BIND_UNSHARED_TRACKERS, BIND_SHARED_TRACKERS,
        INDEX_UNSHARED_TRACKERS, PUBLISH_RELATION_EVENTS
    }

    public enum ItemOutcome { Succeeded, Failed, Skipped }

    public record Request(String parentWorkingSetId, String newWorkingSetId, String requestId,
                          List<String> productSpaceIds, List<String> sharedTrackerIds,
                          List<String> unsharedParentTrackerIds, String name, Mode mode) {
        public Request(String parentWorkingSetId, String newWorkingSetId, String requestId,
                       List<String> productSpaceIds, List<String> sharedTrackerIds,
                       List<String> unsharedParentTrackerIds) {
            this(parentWorkingSetId, newWorkingSetId, requestId, productSpaceIds, sharedTrackerIds,
                    unsharedParentTrackerIds, null, Mode.CREATE);
        }

        public Request(String parentWorkingSetId, String newWorkingSetId, String requestId,
                       List<String> productSpaceIds, List<String> sharedTrackerIds,
                       List<String> unsharedParentTrackerIds, String name) {
            this(parentWorkingSetId, newWorkingSetId, requestId, productSpaceIds, sharedTrackerIds,
                    unsharedParentTrackerIds, name, Mode.CREATE);
        }

        public Request {
            validateId(parentWorkingSetId, "parentWorkingSetId");
            validateId(newWorkingSetId, "newWorkingSetId");
            validateId(requestId, "requestId");
            productSpaceIds = normalize(productSpaceIds);
            sharedTrackerIds = normalize(sharedTrackerIds);
            unsharedParentTrackerIds = normalize(unsharedParentTrackerIds);
            name = normalizeName(name);
            mode = mode == null ? Mode.CREATE : mode;
        }

        public List<String> items(Stage stage) {
            return switch (stage) {
                case CREATE_WORKING_SET -> mode == Mode.CREATE ? List.of(newWorkingSetId) : List.of();
                case BIND_PRODUCT_SPACES -> productSpaceIds;
                case COPY_UNSHARED_TRACKERS, BIND_UNSHARED_TRACKERS, INDEX_UNSHARED_TRACKERS,
                        PUBLISH_RELATION_EVENTS -> unsharedParentTrackerIds;
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

        private static String normalizeName(String name) {
            if (name == null) {
                return null;
            }
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name must not be blank");
            }
            if (trimmed.length() > 255) {
                throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name must contain at most 255 characters");
            }
            return trimmed;
        }

        private static void validateId(String id, String field) {
            if (id == null || id.isBlank() || id.length() > 128 || id.indexOf(0) >= 0) {
                throw new DomainException(WorkingsetResultCode.BAD_REQUEST, field + " must contain 1 to 128 characters");
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

    public record IndexProgress(String afterLocalId, int succeeded, int failed, int skipped,
                                String scannedChildTrackerId) {
        public static IndexProgress initial() {
            return new IndexProgress("", 0, 0, 0, null);
        }
    }

    public WorkingSetCreation {
        failures = List.copyOf(failures);
        stageProgress = stageProgress == null ? Map.of(Stage.CREATE_WORKING_SET, progress) : Map.copyOf(stageProgress);
        indexProgress = indexProgress == null ? Map.of() : Map.copyOf(indexProgress);
    }

    public static WorkingSetCreation submit(String id, Request request, String caller, Instant now) {
        var initial = firstStage(request);
        if (initial == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "working set request has no work");
        }
        return new WorkingSetCreation(id, request, caller, Status.Pending,
                initial, new Progress(0, 0, 0, total(request)), List.of(), now, null, null,
                Map.of(initial, new Progress(0, 0, 0, request.items(initial).size())), Map.of());
    }

    private static Stage firstStage(Request request) {
        for (Stage stage : Stage.values()) {
            if (!request.items(stage).isEmpty()) {
                return stage;
            }
        }
        return null;
    }

    private static int total(Request request) {
        int total = 0;
        for (Stage stage : Stage.values()) {
            total += request.items(stage).size();
        }
        return total;
    }

    public WorkingSetCreation start(Instant now) {
        if (status != Status.Pending) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Only a pending creation can start");
        }
        return new WorkingSetCreation(id, request, requestedBy, Status.Running,
                stage, progress, failures, createdAt, now, null, stageProgress, indexProgress);
    }

    public String currentItem() {
        requireRunning();
        return request.items(stage).get(stageProgress.get(stage).processed());
    }

    public WorkingSetCreation advance(ItemOutcome outcome, Failure failure, Instant now) {
        requireRunning();
        if ((outcome == ItemOutcome.Failed) != (failure != null) || failure != null && failure.stage() != stage) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "Failure detail must match the current step and outcome");
        }
        return advance(outcome, failure == null ? List.of() : List.of(failure), now);
    }

    public WorkingSetCreation recordIndexBatch(IndexProgress batch, List<Failure> batchFailures,
                                                boolean finished, Instant now) {
        if (stage != Stage.INDEX_UNSHARED_TRACKERS) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Not indexing");
        }
        var updatedIndex = new HashMap<>(indexProgress);
        updatedIndex.put(currentItem(), batch);
        if (finished) {
            return advance(batch.failed() == 0 ? ItemOutcome.Succeeded : ItemOutcome.Failed,
                    batchFailures, now, updatedIndex);
        }
        var updatedFailures = new ArrayList<>(failures);
        updatedFailures.addAll(batchFailures);
        return new WorkingSetCreation(id, request, requestedBy, status, stage, progress, updatedFailures,
                createdAt, startedAt, completedAt, stageProgress, updatedIndex);
    }

    private WorkingSetCreation advance(ItemOutcome outcome, List<Failure> newFailures, Instant now) {
        return advance(outcome, newFailures, now, indexProgress);
    }

    private WorkingSetCreation advance(ItemOutcome outcome, List<Failure> newFailures, Instant now,
                                       Map<String, IndexProgress> updatedIndex) {
        var updatedProgress = new EnumMap<Stage, Progress>(Stage.class);
        updatedProgress.putAll(stageProgress);
        var current = updatedProgress.get(stage).record(outcome);
        updatedProgress.put(stage, current);
        var updatedFailures = new ArrayList<>(failures);
        updatedFailures.addAll(newFailures);
        var totals = new Progress(progress.succeeded(), progress.failed(), progress.skipped(), total(request))
                .record(outcome);
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
                createdAt, startedAt, nextStatus == Status.Running ? null : now, updatedProgress, updatedIndex);
    }

    private void requireRunning() {
        if (status != Status.Running) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Only a running creation can finish");
        }
    }
}
