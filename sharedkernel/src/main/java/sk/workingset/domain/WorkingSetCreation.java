package sk.workingset.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jmolecules.ddd.annotation.AggregateRoot;
import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;

import java.time.Instant;
import java.util.*;

/**
 * Durable execution of a working-set materialization request: create a child or extend an existing child.
 */
@AggregateRoot
public record WorkingSetCreation(@JsonProperty("id") @JsonFormat(shape = JsonFormat.Shape.STRING)
                                 Long numericId, Request request, String requestedBy, Status status,
                                 Stage stage, Progress progress, List<Failure> failures,
                                 Instant createdAt, Instant startedAt, Instant completedAt,
                                 Map<Stage, Progress> stageProgress, Map<String, IndexProgress> indexProgress) {
    public WorkingSetCreation {
        failures = List.copyOf(failures);
        stageProgress = stageProgress == null ? Map.of(Stage.CREATE_WORKING_SET, progress) : Map.copyOf(stageProgress);
        indexProgress = indexProgress == null ? Map.of() : Map.copyOf(indexProgress);
    }

    public static WorkingSetCreation submit(String id, Request request, String caller, Instant now) {
        WorkingSetCreation.Stage initial = firstStage(request);
        if (initial == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "working set request has no work");
        }
        return new WorkingSetCreation(Long.valueOf(id), request, caller, Status.Pending,
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

    public String id() {
        return numericId.toString();
    }

    public WorkingSetCreation start(Instant now) {
        if (status != Status.Pending) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Only a pending creation can start");
        }
        return new WorkingSetCreation(numericId, request, requestedBy, Status.Running,
                stage, progress, failures, createdAt, now, null, stageProgress, indexProgress);
    }

    public String currentItem() {
        requireRunning();
        return request.items(stage).get(stageProgress.get(stage).processed());
    }

    public WorkingSetCreation advance(ItemOutcome outcome, Failure failure, Instant now) {
        requireRunning();
        if ((outcome == ItemOutcome.Failed) == (failure == null) || failure != null && failure.stage() != stage) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "Failure detail must match the current step and outcome");
        }
        return advance(outcome, failure == null ? List.of() : List.of(failure), now);
    }

    public WorkingSetCreation recordIndexBatch(IndexProgress batch, List<Failure> batchFailures,
                                               boolean finished, Instant now) {
        if (stage != Stage.INDEX_UNSHARED_TRACKERS) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Not indexing");
        }
        HashMap<String, WorkingSetCreation.IndexProgress> updatedIndex = new HashMap<>(indexProgress);
        updatedIndex.put(currentItem(), batch);
        if (finished) {
            return advance(batch.failed() == 0 ? ItemOutcome.Succeeded : ItemOutcome.Failed,
                    batchFailures, now, updatedIndex);
        }
        ArrayList<WorkingSetCreation.Failure> updatedFailures = new ArrayList<>(failures);
        updatedFailures.addAll(batchFailures);
        return new WorkingSetCreation(numericId, request, requestedBy, status, stage, progress, updatedFailures,
                createdAt, startedAt, completedAt, stageProgress, updatedIndex);
    }

    private WorkingSetCreation advance(ItemOutcome outcome, List<Failure> newFailures, Instant now) {
        return advance(outcome, newFailures, now, indexProgress);
    }

    private WorkingSetCreation advance(ItemOutcome outcome, List<Failure> newFailures, Instant now,
                                       Map<String, IndexProgress> updatedIndex) {
        EnumMap<WorkingSetCreation.Stage, WorkingSetCreation.Progress> updatedProgress = new EnumMap<Stage, Progress>(Stage.class);
        updatedProgress.putAll(stageProgress);
        WorkingSetCreation.Progress current = updatedProgress.get(stage).record(outcome);
        updatedProgress.put(stage, current);
        ArrayList<WorkingSetCreation.Failure> updatedFailures = new ArrayList<>(failures);
        updatedFailures.addAll(newFailures);
        WorkingSetCreation.Progress totals = new Progress(progress.succeeded(), progress.failed(), progress.skipped(), total(request))
                .record(outcome);
        WorkingSetCreation.Stage next = stage;
        WorkingSetCreation.Status nextStatus = Status.Running;
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
        return new WorkingSetCreation(numericId, request, requestedBy, nextStatus, next, totals, updatedFailures,
                createdAt, startedAt, nextStatus == Status.Running ? null : now, updatedProgress, updatedIndex);
    }

    private void requireRunning() {
        if (status != Status.Running) {
            throw new DomainException(WorkingsetResultCode.INVALID_DOMAIN_STATE, "Only a running creation can finish");
        }
    }

    public enum Status {Pending, Running, Succeeded, PartiallySucceeded, Failed}

    public enum Mode {CREATE, EXTEND}

    public enum Stage {
        CREATE_WORKING_SET, BIND_PRODUCT_SPACES, COPY_UNSHARED_TRACKERS, BIND_UNSHARED_TRACKERS, BIND_SHARED_TRACKERS,
        INDEX_UNSHARED_TRACKERS, PUBLISH_RELATION_EVENTS
    }

    public enum ItemOutcome {Succeeded, Failed, Skipped}

    public record Request(@JsonProperty("parentWorkingSetId") Long numericParentWorkingSetId,
                          @JsonProperty("newWorkingSetId") Long numericTargetWorkingSetId, String requestId,
                          @JsonProperty("productSpaceIds") List<Long> numericProductSpaceIds,
                          @JsonProperty("sharedTrackerIds") List<Long> numericSharedTrackerIds,
                          @JsonProperty("unsharedParentTrackerIds") List<Long> numericUnsharedTrackerIds,
                          String name, Mode mode, String color) {
        public Request(String parentWorkingSetId, String newWorkingSetId, String requestId,
                       List<String> productSpaceIds, List<String> sharedTrackerIds,
                       List<String> unsharedParentTrackerIds, String name, Mode mode) {
            this(parentWorkingSetId, newWorkingSetId, requestId, productSpaceIds, sharedTrackerIds,
                    unsharedParentTrackerIds, name, mode, null);
        }

        public Request(String parentWorkingSetId, String newWorkingSetId, String requestId,
                       List<String> productSpaceIds, List<String> sharedTrackerIds,
                       List<String> unsharedParentTrackerIds, String name, Mode mode, String color) {
            this(WorkingSetId.of(parentWorkingSetId).numericValue(), WorkingSetId.of(newWorkingSetId).numericValue(),
                    requestId, parseIds(productSpaceIds), parseIds(sharedTrackerIds),
                    parseIds(unsharedParentTrackerIds), name, mode, color);
        }

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
            WorkingSetId.of(numericParentWorkingSetId);
            WorkingSetId.of(numericTargetWorkingSetId);
            validateId(requestId, "requestId");
            numericProductSpaceIds = normalize(numericProductSpaceIds);
            numericSharedTrackerIds = normalize(numericSharedTrackerIds);
            numericUnsharedTrackerIds = normalize(numericUnsharedTrackerIds);
            name = WorkingSet.normalizeName(name);
            mode = mode == null ? Mode.CREATE : mode;
            color = mode == Mode.CREATE ? WorkingSet.normalizeColor(color) : null;
        }

        private static List<Long> parseIds(List<String> ids) {
            return ids == null ? List.of() : ids.stream().map(Long::valueOf).toList();
        }

        private static List<Long> normalize(List<Long> ids) {
            if (ids == null) return List.of();
            if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
                throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "selection IDs must be positive");
            }
            return List.copyOf(new TreeSet<>(ids));
        }

        private static List<String> formatIds(List<Long> ids) {
            return ids.stream().map(String::valueOf).toList();
        }

        private static void validateId(String id, String field) {
            if (id == null || id.isBlank() || id.length() > 128 || id.indexOf(0) >= 0) {
                throw new DomainException(WorkingsetResultCode.BAD_REQUEST, field + " must contain 1 to 128 characters");
            }
        }

        public String parentWorkingSetId() {
            return numericParentWorkingSetId.toString();
        }

        public String newWorkingSetId() {
            return numericTargetWorkingSetId.toString();
        }

        public List<String> productSpaceIds() {
            return formatIds(numericProductSpaceIds);
        }

        public List<String> sharedTrackerIds() {
            return formatIds(numericSharedTrackerIds);
        }

        public List<String> unsharedParentTrackerIds() {
            return formatIds(numericUnsharedTrackerIds);
        }

        public List<String> items(Stage stage) {
            return switch (stage) {
                case CREATE_WORKING_SET -> mode == Mode.CREATE ? List.of(newWorkingSetId()) : List.of();
                case BIND_PRODUCT_SPACES -> productSpaceIds();
                case COPY_UNSHARED_TRACKERS, BIND_UNSHARED_TRACKERS, INDEX_UNSHARED_TRACKERS,
                     PUBLISH_RELATION_EVENTS -> unsharedParentTrackerIds();
                case BIND_SHARED_TRACKERS -> sharedTrackerIds();
            };
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
                          String reason, Instant occurredAt) {
    }

    public record IndexProgress(String afterLocalId, int succeeded, int failed, int skipped,
                                String scannedChildTrackerId) {
        public static IndexProgress initial() {
            return new IndexProgress("", 0, 0, 0, null);
        }
    }
}
