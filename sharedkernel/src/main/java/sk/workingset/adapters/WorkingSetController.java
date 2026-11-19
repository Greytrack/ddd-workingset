package sk.workingset.adapters;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sk.common.error.DomainException;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.command.WorkingSetCommandService;
import sk.workingset.application.query.WorkingSetQueryService;
import sk.workingset.application.query.model.*;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.lighttrackeritem.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.error.WorkingsetResultCode;

import java.util.List;

/**
 * Working Set 的查询、绑定、解析与读取入口。批量派生走 T7 扩展任务，不再提供单 Item 派生入口。
 */
@RestController
@RequestMapping("/api/worksets")
public class WorkingSetController {

    private final WorkingSetCommandService commands;
    private final WorkingSetQueryService queries;

    public WorkingSetController(WorkingSetCommandService commands, WorkingSetQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

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

    @PostMapping("/{workingSetId}/children")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkingSetView createChild(@PathVariable String workingSetId, @RequestBody CreateChildRequest request) {
        return WorkingSetView.from(commands.createChildWorkingSet(WorkingSetId.of(workingSetId),
                request.name(), request.color()));
    }

    @GetMapping("/{workingSetId}/children")
    public List<String> findChildren(@PathVariable String workingSetId) {
        return queries.findChildren(WorkingSetId.of(workingSetId)).stream().map(String::valueOf).toList();
    }

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

    @GetMapping("/metadata/classifications")
    public List<ClassificationView> findClassifications() {
        return queries.findClassifications().stream().map(ClassificationView::from).toList();
    }

    // Query-side model has the same simple name as the REST view; keep the conversion explicit.

    @GetMapping("/{workingSetId}/bindings")
    public BindingsView listBindings(@PathVariable String workingSetId) {
        return BindingsView.from(queries.findBindings(WorkingSetId.of(workingSetId)));
    }

    @PostMapping("/{workingSetId}/spaces/{spaceId}")
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceBindingView bindSpace(@PathVariable String workingSetId, @PathVariable String spaceId) {
        return SpaceBindingView.from(commands.bindSpace(WorkingSetId.of(workingSetId), spaceId));
    }

    @PostMapping("/{workingSetId}/trackers/shared/{trackerId}")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackerBindingView bindTrackerShared(@PathVariable String workingSetId, @PathVariable String trackerId) {
        return TrackerBindingView.from(commands.bindTrackerShared(
                WorkingSetId.of(workingSetId), TrackerId.of(trackerId)));
    }

    @GetMapping("/{workingSetId}/items/{localId}")
    public LightTrackerItemView resolve(@PathVariable("workingSetId") String workingSetId,
                                        @PathVariable("localId") String localId) {
        LightTrackerItem item = queries.resolve(WorkingSetId.of(workingSetId), LocalId.of(localId));
        return LightTrackerItemView.from(item);
    }

    @PatchMapping("/{workingSetId}/trackers/{trackerId}/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setTrackerShown(@PathVariable String workingSetId, @PathVariable String trackerId,
                                @RequestBody TrackerVisibilityRequest request) {
        if (request.shown() == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "shown is required");
        }
        commands.setTrackerShown(WorkingSetId.of(workingSetId), TrackerId.of(trackerId), request.shown());
    }

    public record CreateChildRequest(String name, String color) {
    }

    public record UpdateWorkingSetRequest(String name, String color) {
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

    public record ClassificationView(String classificationId, SharingMode sharingMode, String name) {
        static ClassificationView from(ClassificationSummary summary) {
            return new ClassificationView(String.valueOf(summary.classificationId()),
                    SharingMode.valueOf(summary.sharingMode()), summary.name());
        }
    }

    public record SpaceBindingView(String productSpaceId, String name, String origin, String state) {
        static SpaceBindingView from(WorkingSet.SpaceBinding binding) {
            return new SpaceBindingView(binding.productSpaceId(), binding.name(),
                    binding.origin().name(), binding.state().name());
        }

        static SpaceBindingView from(SpaceBindingSummary summary) {
            return new SpaceBindingView(String.valueOf(summary.productSpaceId()), summary.name(),
                    summary.origin(), summary.state());
        }
    }

    public record TrackerVisibilityRequest(Boolean shown) {
    }

    public record TrackerBindingView(String trackerId, String productSpaceId, String classificationId,
                                     String sharingMode, String origin, boolean shown, boolean rootFolder) {
        static TrackerBindingView from(WorkingSet.TrackerBinding binding) {
            return new TrackerBindingView(binding.trackerId().value(), binding.productSpaceId(),
                    binding.classificationId(), binding.sharingMode().name(), binding.origin().name(), true, false);
        }

        static TrackerBindingView from(TrackerBindingSummary summary) {
            return new TrackerBindingView(String.valueOf(summary.trackerId()),
                    String.valueOf(summary.productSpaceId()), String.valueOf(summary.classificationId()),
                    summary.sharingMode(), summary.origin(), summary.shown(), summary.rootFolder());
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
