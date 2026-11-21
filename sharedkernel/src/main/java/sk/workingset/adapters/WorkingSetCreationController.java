package sk.workingset.adapters;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import sk.workingset.application.command.WorkingSetCreationService;
import sk.workingset.domain.workingset.WorkingSetCreation;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作集创建任务的 HTTP 入口；提交后异步执行，客户端轮询 {@code GET /api/working-set-creations/{id}} 至终态。
 */
@RestController
@RequestMapping("/api/working-set-creations")
public class WorkingSetCreationController {
    private final WorkingSetCreationService creations;

    public WorkingSetCreationController(WorkingSetCreationService creations) {
        this.creations = creations;
    }

    /**
     * 提交创建工作集任务，返回 202 与任务视图；requestId 为幂等键。
     */
    @PostMapping
    public ResponseEntity<CreationView> submit(@RequestBody CreateWorkingSetRequest request) {
        WorkingSetCreation creation = creations.submitNew(request.requestId(), request::toDomainRequest);
        return ResponseEntity.accepted().location(URI.create("/api/working-set-creations/" + creation.id()))
                .body(CreationView.from(creation));
    }

    /**
     * 按 id 查询创建任务的状态、阶段、进度与失败明细。
     */
    @GetMapping("/{id}")
    public CreationView find(@PathVariable String id) {
        return CreationView.from(creations.find(id));
    }

    /**
     * 任务上下文读取创建中的目标工作集；普通 /api/worksets/{id} 仍返回 Preparing。
     */
    @GetMapping("/{id}/working-set")
    public WorkingSetController.WorkingSetView findTarget(@PathVariable String id) {
        return WorkingSetController.WorkingSetView.from(creations.findTarget(id));
    }

    public record CreationView(String creationId, String targetWorkingSetId, String mode,
                               WorkingSetCreation.Status status,
                               WorkingSetCreation.Stage stage,
                               WorkingSetCreation.Progress progress, List<WorkingSetCreation.Failure> failures,
                               Instant createdAt, Instant startedAt, Instant completedAt,
                               Map<Long, Instant> baselineAtByTracker,
                               Map<WorkingSetCreation.Stage, WorkingSetCreation.Progress> stageProgress,
                               Map<String, IndexCounts> indexProgress) {
        static CreationView from(WorkingSetCreation creation) {
            return new CreationView(creation.id(), creation.request().newWorkingSetId(),
                    creation.request().mode().name(), creation.status(), creation.stage(),
                    creation.progress(),
                    creation.failures(), creation.createdAt(), creation.startedAt(), creation.completedAt(),
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
