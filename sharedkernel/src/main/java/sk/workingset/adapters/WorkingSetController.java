package sk.workingset.adapters;

import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sk.workingset.application.Caller;
import sk.workingset.application.ReadResult;
import sk.workingset.application.WorkingSetApplicationService;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.WorkingSet;

import java.util.List;

/**
 * Working Set 的查询、绑定、解析与读取入口。批量派生走 T7 扩展任务，不再提供单 Item 派生入口。
 */
@RestController
@RequestMapping("/api/worksets")
public class WorkingSetController {

    private static final Caller WEB_CALLER = Caller.of("web");

    private final WorkingSetApplicationService applicationService;

    public WorkingSetController(WorkingSetApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/{workingSetId}")
    public WorkingSetView find(@PathVariable String workingSetId) {
        var workingSet = applicationService.findWorkingSet(WorkingSetId.of(workingSetId));
        return WorkingSetView.from(workingSet);
    }

    @PostMapping("/{workingSetId}/children")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkingSetView createChild(@PathVariable String workingSetId, @RequestBody CreateChildRequest request) {
        return WorkingSetView.from(applicationService.createChildWorkingSet(WorkingSetId.of(workingSetId),
                WorkingSetId.of(requireText(request.workingSetId(), "workingSetId"))));
    }

    @GetMapping("/{workingSetId}/children")
    public List<String> findChildren(@PathVariable String workingSetId) {
        return applicationService.findChildren(WorkingSetId.of(workingSetId)).stream().map(WorkingSetId::value).toList();
    }

    @GetMapping("/{workingSetId}/ancestors")
    public List<String> findAncestors(@PathVariable String workingSetId) {
        return applicationService.findAncestors(WorkingSetId.of(workingSetId)).stream().map(WorkingSetId::value).toList();
    }

    /** 界面只读列表：工作集内的 Tracker（含 parentId/sourceId，用于构建 Tracker 树）。 */
    @GetMapping("/{workingSetId}/trackers")
    public List<TrackerView> findTrackers(@PathVariable String workingSetId) {
        return applicationService.findTrackers(WorkingSetId.of(workingSetId)).stream().map(TrackerView::from).toList();
    }

    @GetMapping("/metadata/classifications")
    public List<ClassificationView> findClassifications() {
        return applicationService.findClassifications().stream()
                .map(classification -> new ClassificationView(classification.id(), classification.sharingMode()))
                .toList();
    }

    @PostMapping("/{workingSetId}/trackers")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackerView createTracker(@PathVariable String workingSetId, @RequestBody CreateTrackerRequest request) {
        var mode = SharingMode.valueOf(requireText(request.sharingMode(), "sharingMode"));
        return TrackerView.from(applicationService.createTracker(WorkingSetId.of(workingSetId),
                requireText(request.productSpaceId(), "productSpaceId"), requireText(request.name(), "name"),
                requireText(request.classificationId(), "classificationId"), mode));
    }

    /** 界面只读列表：当前工作集已绑定的 Tracker 下的 LightTrackerItem。 */
    @GetMapping("/{workingSetId}/trackers/{trackerId}/items")
    public List<LightTrackerItemView> findItems(@PathVariable String workingSetId, @PathVariable String trackerId) {
        return applicationService.findItems(WorkingSetId.of(workingSetId), TrackerId.of(trackerId)).stream()
                .map(indexed -> LightTrackerItemView.from(indexed.item(), indexed.name())).toList();
    }

    @GetMapping("/{workingSetId}/bindings")
    public BindingsView listBindings(@PathVariable String workingSetId) {
        var id = WorkingSetId.of(workingSetId);
        var workingSet = applicationService.findWorkingSet(id);
        var visible = applicationService.findTrackers(id).stream().map(Tracker::trackerId)
                .collect(java.util.stream.Collectors.toSet());
        return new BindingsView(workingSet.spaces().stream().map(SpaceBindingView::from).toList(),
                workingSet.trackers().stream()
                        .filter(binding -> visible.contains(binding.trackerId()))
                        .map(TrackerBindingView::from).toList());
    }

    /**
     * 创建产品空间。路径中的 {@code workingSetId} 仅是入口占位，不参与目标工作集选择：
     * 产品空间只能创建在默认工作集，服务层固定使用默认工作集；入口层不做来源限制。
     */
    @PostMapping("/{workingSetId}/product-spaces")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductSpaceView createProductSpace(@PathVariable String workingSetId,
                                               @RequestBody CreateProductSpaceRequest request) {
        return ProductSpaceView.from(applicationService.createProductSpace(
                request.productSpaceId(), request.name()));
    }

    @PostMapping("/{workingSetId}/spaces/{spaceId}")
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceBindingView bindSpace(@PathVariable String workingSetId, @PathVariable String spaceId) {
        return SpaceBindingView.from(applicationService.bindSpace(WorkingSetId.of(workingSetId), spaceId));
    }

    @PostMapping("/{workingSetId}/trackers/shared/{trackerId}")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackerBindingView bindTrackerShared(@PathVariable String workingSetId, @PathVariable String trackerId) {
        return TrackerBindingView.from(applicationService.bindTrackerShared(
                WorkingSetId.of(workingSetId), TrackerId.of(trackerId)));
    }

    @GetMapping("/{workingSetId}/items/{localId}")
    public LightTrackerItemView resolve(@PathVariable("workingSetId") String workingSetId,
                                        @PathVariable("localId") String localId) {
        LightTrackerItem item = applicationService.resolve(
                WorkingSetId.of(workingSetId), LocalId.of(localId));
        return LightTrackerItemView.from(item);
    }

    @GetMapping("/{workingSetId}/items/{localId}/content")
    public ReadView read(@PathVariable("workingSetId") String workingSetId,
                         @PathVariable("localId") String localId) {
        ReadResult result = applicationService.read(
                WorkingSetId.of(workingSetId), LocalId.of(localId), WEB_CALLER);
        return ReadView.from(result);
    }

    public record CreateProductSpaceRequest(String productSpaceId, String name) {}

    public record ProductSpaceView(String productSpaceId, String name) {
        static ProductSpaceView from(sk.workingset.domain.ProductSpace space) {
            return new ProductSpaceView(space.id(), space.name());
        }
    }

    public record CreateChildRequest(String workingSetId) {}

    public record CreateTrackerRequest(String productSpaceId, String name, String classificationId,
                                       String sharingMode) {}

    public record ClassificationView(String classificationId, SharingMode sharingMode) {}

    public record WorkingSetView(String workingSetId, String name, boolean defaultWorkingSet,
                                 String parentWorkingSetId) {
        static WorkingSetView from(WorkingSet workingSet) {
            WorkingSetId parentId = workingSet.parentWorkingSetId();
            return new WorkingSetView(workingSet.id().value(), workingSet.name(),
                    WorkingSet.DEFAULT_ID.equals(workingSet.id()),
                    parentId == null ? null : parentId.value());
        }
    }

    public record SpaceBindingView(String productSpaceId, String name, String origin, String state) {
        static SpaceBindingView from(WorkingSet.SpaceBinding binding) {
            return new SpaceBindingView(binding.productSpaceId(), binding.name(),
                    binding.origin().name(), binding.state().name());
        }
    }

    public record TrackerBindingView(String trackerId, String productSpaceId, String classificationId,
                                     String sharingMode, String origin) {
        static TrackerBindingView from(WorkingSet.TrackerBinding binding) {
            return new TrackerBindingView(binding.trackerId().value(), binding.productSpaceId(),
                    binding.classificationId(), binding.sharingMode().name(), binding.origin().name());
        }
    }

    public record BindingsView(List<SpaceBindingView> spaces, List<TrackerBindingView> trackers) {}

    public record TrackerView(String trackerId, String name, String parentId, String sourceId, String productSpaceId,
                              String classificationId, String sharingMode, boolean isDefault) {
        static TrackerView from(Tracker tracker) {
            return new TrackerView(tracker.trackerId().value(), tracker.name(),
                    tracker.parentId() == null ? null : tracker.parentId().value(),
                    tracker.sourceId() == null ? null : tracker.sourceId().value(),
                    tracker.productSpaceId(), tracker.classificationId(), tracker.sharingMode().name(),
                    tracker.isDefault());
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, name + " must not be blank");
        }
        return value;
    }
}
