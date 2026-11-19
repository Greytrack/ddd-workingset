package sk.workingset.adapters;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sk.common.error.DomainException;
import sk.workingset.application.command.WorkingSetCommandService;
import sk.workingset.application.query.WorkingSetQueryService;
import sk.workingset.application.query.model.*;
import sk.workingset.domain.Classification.SharingMode;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.*;
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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, name + " must not be blank");
        }
        return value;
    }

    @GetMapping("/{workingSetId}")
    public WorkingSetView find(@PathVariable String workingSetId) {
        return WorkingSetView.from(queries.find(WorkingSetId.of(workingSetId)));
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

    /**
     * 创建产品空间。路径中的 {@code workingSetId} 仅是入口占位，不参与目标工作集选择：
     * 产品空间只能创建在默认工作集，服务层固定使用默认工作集；入口层不做来源限制。
     */
    @PostMapping("/{workingSetId}/product-spaces")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductSpaceView createProductSpace(@PathVariable String workingSetId,
                                               @RequestBody CreateProductSpaceRequest request) {
        return ProductSpaceView.from(commands.createProductSpace(requireText(request.name(), "name")));
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

    public record CreateProductSpaceRequest(String name) {
    }

    public record ProductSpaceView(String productSpaceId, String name) {
        static ProductSpaceView from(sk.workingset.domain.ProductSpace space) {
            return new ProductSpaceView(space.id(), space.name());
        }
    }

    public record CreateChildRequest(String name, String color) {
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

    public record TrackerBindingView(String trackerId, String productSpaceId, String classificationId,
                                     String sharingMode, String origin) {
        static TrackerBindingView from(WorkingSet.TrackerBinding binding) {
            return new TrackerBindingView(binding.trackerId().value(), binding.productSpaceId(),
                    binding.classificationId(), binding.sharingMode().name(), binding.origin().name());
        }

        static TrackerBindingView from(TrackerBindingSummary summary) {
            return new TrackerBindingView(String.valueOf(summary.trackerId()),
                    String.valueOf(summary.productSpaceId()), String.valueOf(summary.classificationId()),
                    summary.sharingMode(), summary.origin());
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
