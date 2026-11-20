package sk.workingset.adapters;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sk.workingset.application.command.WorkingSetCommandService;
import sk.workingset.application.command.WorkingSetDeletionService;
import sk.workingset.application.query.WorkingSetQueryService;
import sk.workingset.application.query.model.*;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;

import java.util.List;

/**
 * Working Set 的查询与管理入口。范围纳入由创建和扩展任务处理。
 */
@RestController
@RequestMapping("/api/worksets")
public class WorkingSetController {

    private final WorkingSetCommandService commands;
    private final WorkingSetQueryService queries;
    private final WorkingSetDeletionService deletions;

    public WorkingSetController(WorkingSetCommandService commands, WorkingSetQueryService queries,
                                WorkingSetDeletionService deletions) {
        this.commands = commands;
        this.queries = queries;
        this.deletions = deletions;
    }

    @DeleteMapping("/{workingSetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkingSet(@PathVariable String workingSetId) {
        deletions.deleteWorkingSet(WorkingSetId.of(workingSetId));
    }

    /**
     * 按 id 查询工作集；创建中的目标工作集返回 Preparing（任务上下文读取见 {@link WorkingSetCreationController#findTarget}）。
     */
    @GetMapping("/{workingSetId}")
    public WorkingSetView find(@PathVariable String workingSetId) {
        return WorkingSetView.from(queries.find(WorkingSetId.of(workingSetId)));
    }

    /**
     * 编辑工作集显示名称与主题色；请求中缺失的字段保持不变。
     */
    @PatchMapping("/{workingSetId}")
    public WorkingSetView updatePresentation(@PathVariable String workingSetId,
                                             @RequestBody UpdateWorkingSetRequest request) {
        return WorkingSetView.from(commands.updatePresentation(WorkingSetId.of(workingSetId),
                request.name(), request.color()));
    }

    /**
     * 创建子工作集，形成单父谱系；名称与主题色可空。
     */
    @PostMapping("/{workingSetId}/children")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkingSetView createChild(@PathVariable String workingSetId, @RequestBody CreateChildRequest request) {
        return WorkingSetView.from(commands.createChildWorkingSet(WorkingSetId.of(workingSetId),
                request.name(), request.color()));
    }

    /**
     * 查询直接子工作集的 id 列表。
     */
    @GetMapping("/{workingSetId}/children")
    public List<String> findChildren(@PathVariable String workingSetId) {
        return queries.findChildren(WorkingSetId.of(workingSetId)).stream().map(String::valueOf).toList();
    }

    /**
     * 查询祖先工作集的 id 列表。
     */
    @GetMapping("/{workingSetId}/ancestors")
    public List<String> findAncestors(@PathVariable String workingSetId) {
        return queries.findAncestors(WorkingSetId.of(workingSetId)).stream().map(String::valueOf).toList();
    }

    /**
     * 界面只读列表：工作集内的 Tracker（含 parentId/sourceId，用于构建 Tracker 树）。
     */
    @GetMapping("/{workingSetId}/trackers")
    public List<TrackerView> findTrackers(@PathVariable String workingSetId) {
        return queries.findTrackers(WorkingSetId.of(workingSetId)).stream().map(TrackerView::from).toList();
    }

    /**
     * 查询可用的分类元数据（含共享特性），供创建 Tracker 表单使用（T2 §4.4）。
     */
    @GetMapping("/metadata/classifications")
    public List<ClassificationView> findClassifications() {
        return queries.findClassifications().stream().map(ClassificationView::from).toList();
    }

    // Query-side model has the same simple name as the REST view; keep the conversion explicit.

    /**
     * 查询工作集的空间绑定与 Tracker 绑定。
     */
    @GetMapping("/{workingSetId}/bindings")
    public BindingsView listBindings(@PathVariable String workingSetId) {
        return BindingsView.from(queries.findBindings(WorkingSetId.of(workingSetId)));
    }

    public record WorkingSetView(String workingSetId, String name, boolean defaultWorkingSet,
                                 String parentWorkingSetId, String color) {
        static WorkingSetView from(WorkingSet workingSet) {
            WorkingSetId parentId = workingSet.parentWorkingSetId();
            return new WorkingSetView(workingSet.id().value(), workingSet.name(),
                    WorkingSet.DEFAULT_ID.equals(workingSet.id()),
                    parentId == null ? null : parentId.value(), workingSet.color());
        }

        static WorkingSetView from(WorkingSetSummary summary) {
            return new WorkingSetView(String.valueOf(summary.workingSetId()), summary.name(),
                    summary.defaultWorkingSet(),
                    summary.parentWorkingSetId() == null ? null : String.valueOf(summary.parentWorkingSetId()),
                    summary.color());
        }
    }

    public record ClassificationView(String classificationId, SharingMode sharingMode, String name,
                                     String baseClassId, String classId) {
        static ClassificationView from(ClassificationSummary summary) {
            return new ClassificationView(String.valueOf(summary.classificationId()),
                    SharingMode.valueOf(summary.sharingMode()), summary.name(),
                    String.valueOf(summary.baseClassId()), String.valueOf(summary.classId()));
        }
    }

    public record SpaceBindingView(String productSpaceId, String name, String origin, String state) {
        static SpaceBindingView from(SpaceBindingSummary summary) {
            return new SpaceBindingView(String.valueOf(summary.productSpaceId()), summary.name(),
                    summary.origin(), summary.state());
        }
    }

    public record TrackerBindingView(String trackerId, String productSpaceId, String classificationId,
                                     String sharingMode, String origin, boolean shown) {
        static TrackerBindingView from(TrackerBindingSummary summary) {
            return new TrackerBindingView(String.valueOf(summary.trackerId()),
                    String.valueOf(summary.productSpaceId()), String.valueOf(summary.classificationId()),
                    summary.sharingMode(), summary.origin(), summary.shown());
        }
    }

    public record BindingsView(List<SpaceBindingView> spaces, List<TrackerBindingView> trackers) {
        static BindingsView from(sk.workingset.application.query.model.BindingsView model) {
            return new BindingsView(model.spaces().stream().map(SpaceBindingView::from).toList(),
                    model.trackers().stream().map(TrackerBindingView::from).toList());
        }
    }

    public record TrackerView(String trackerId, String name, String parentId, String sourceId, String productSpaceId,
                              String classificationId, String sharingMode, boolean isDefault) {
        static TrackerView from(TrackerSummary summary) {
            return new TrackerView(String.valueOf(summary.trackerId()), summary.name(),
                    summary.parentId() == null ? null : String.valueOf(summary.parentId()),
                    summary.sourceId() == null ? null : String.valueOf(summary.sourceId()),
                    String.valueOf(summary.productSpaceId()), String.valueOf(summary.classificationId()),
                    summary.sharingMode(), summary.isDefault());
        }
    }
}
