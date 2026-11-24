package com.airedgesoft.sharedkernel.workingset.adapters;

import com.airedgesoft.lib.restful.idempotency.IdempotencyFilter;
import com.airedgesoft.sharedkernel.businessframework.application.query.QWorkingset;
import com.airedgesoft.sharedkernel.workingset.adapters.self.CreateWorkingSetRequest;
import com.airedgesoft.sharedkernel.workingset.application.command.WorkingSetCreationService;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作集创建任务的 HTTP 入口；提交后异步执行，客户端轮询 {@code GET /v1/workingsetCreations/{id}} 至终态。
 */
@RestController
@ApiResponse(responseCode = "400", description = "请求参数无效", content =
        @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "资源不存在", content =
        @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@RequestMapping("/v1/workingsetCreations")
public class WorkingSetCreationController {
    private final WorkingSetCreationService creations;

    public WorkingSetCreationController(WorkingSetCreationService creations) {
        this.creations = creations;
    }

    /**
     * 提交创建工作集任务，返回 202 与任务视图；Idempotency-Key 参与持久化去重。
     */
    @Operation(operationId = "workingsetCreationSubmit", summary = "提交工作集创建任务")
    @ApiResponse(responseCode = "202", description = "提交工作集创建任务")
    @PostMapping
    public ResponseEntity<CreationView> submit(
            @RequestHeader(name = IdempotencyFilter.KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody CreateWorkingSetRequest request) {
        String key = WorkingsetRequestKey.resolve(idempotencyKey, request.requestId());
        WorkingSetCreation creation = creations.submitNew(key, id -> request.toDomainRequest(id, key));
        return accepted(creation);
    }

    static ResponseEntity<CreationView> accepted(WorkingSetCreation creation) {
        CreationView view = CreationView.from(creation);
        return ResponseEntity.accepted().location(URI.create("/v1/" + view.name())).body(view);
    }

    /**
     * 按 id 查询创建任务的状态、阶段、进度与失败明细。
     */
    @Operation(operationId = "workingsetCreationGet", summary = "读取创建任务状态和进度")
    @ApiResponse(responseCode = "200", description = "读取创建任务状态和进度")
    @GetMapping("/{id}")
    public CreationView find(@PathVariable("id") Long id) {
        return CreationView.from(creations.find(Long.toString(id)));
    }

    /**
     * 任务上下文读取创建中的目标工作集；普通 /v1/workingsets/{id} 仍返回 Preparing。
     */
    @Operation(operationId = "workingsetCreationGetTarget", summary = "读取任务中的目标工作集")
    @ApiResponse(responseCode = "200", description = "读取任务中的目标工作集")
    @GetMapping("/{id}/workingset")
    public QWorkingset findTarget(@PathVariable("id") Long id) {
        return WorkingSetController.toView(creations.findTarget(Long.toString(id)));
    }

    public record CreationView(String name, String creationId, String targetWorkingSetId, String mode,
                               WorkingSetCreation.Status status,
                               WorkingSetCreation.Stage stage,
                               WorkingSetCreation.Progress progress, List<WorkingSetCreation.Failure> failures,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) Instant createTime,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) Instant startTime,
                               @JsonFormat(shape = JsonFormat.Shape.STRING) Instant completionTime,
                               Map<Long, Long> baselineAtByTracker,
                               Map<WorkingSetCreation.Stage, WorkingSetCreation.Progress> stageProgress,
                               Map<String, IndexCounts> indexProgress) {
        private static Instant instant(Long timestamp) {
            return timestamp == null ? null : Instant.ofEpochMilli(timestamp);
        }

        static CreationView from(WorkingSetCreation creation) {
            return new CreationView("workingsetCreations/" + creation.id(), creation.id(), creation.request().newWorkingSetId(),
                    creation.request().mode().name(), creation.status(), creation.stage(),
                    creation.progress(),
                    creation.failures(), Instant.ofEpochMilli(creation.createdAt()), instant(creation.startedAt()),
                    instant(creation.completedAt()),
                    creation.baselineAtByTracker(),
                    creation.stageProgress(), creation.indexProgress().entrySet().stream().collect(
                    Collectors.toUnmodifiableMap(Map.Entry::getKey,
                            entry -> IndexCounts.from(entry.getValue()))));
        }
    }

    public record IndexCounts(int succeeded, int failed, int skipped) {
        static IndexCounts from(WorkingSetCreation.IndexProgress progress) {
            return new IndexCounts(progress.succeeded(), progress.failed(), progress.skipped());
        }
    }
}
