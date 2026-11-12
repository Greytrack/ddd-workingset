package bootstrap.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sk.workingset.application.Caller;
import sk.workingset.application.DeriveCommand;
import sk.workingset.application.DeriveResult;
import sk.workingset.application.ReadResult;
import sk.workingset.application.WorkingSetApplicationService;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.WorkingSet;

import java.util.List;

/**
 * Working Set 的最小 REST 入口：非共享派生、解析与读取。
 */
@RestController
@RequestMapping("/api/worksets")
public class WorkingSetController {

    private static final Caller WEB_CALLER = Caller.of("web");

    private final WorkingSetApplicationService applicationService;

    public WorkingSetController(WorkingSetApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/{workingSetId}/derive")
    @ResponseStatus(HttpStatus.CREATED)
    public DeriveView derive(@PathVariable("workingSetId") String workingSetId,
                             @RequestBody DeriveRequest request) {
        return deriveView(applicationService.derive(command(workingSetId, request)));
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

    @GetMapping("/{workingSetId}/bindings")
    public BindingsView listBindings(@PathVariable String workingSetId) {
        var workingSet = applicationService.listBindings(WorkingSetId.of(workingSetId));
        return new BindingsView(workingSet.spaces().stream().map(SpaceBindingView::from).toList(),
                workingSet.trackers().stream().map(TrackerBindingView::from).toList());
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

    @PostMapping("/{workingSetId}/spaces/{spaceId}/classifications/{classificationId}/derive")
    @ResponseStatus(HttpStatus.CREATED)
    public DeriveView bindTrackerDerived(@PathVariable String workingSetId, @PathVariable String spaceId,
                                        @PathVariable String classificationId, @RequestBody DeriveRequest request) {
        return deriveView(applicationService.bindTrackerDerived(
                spaceId, classificationId, command(workingSetId, request)));
    }

    private static DeriveCommand command(String workingSetId, DeriveRequest request) {
        return new DeriveCommand(WorkingSetId.of(workingSetId),
                TrackerId.of(requireText(request.sourceTrackerId(), "sourceTrackerId")),
                LocalId.of(requireText(request.sourceLocalId(), "sourceLocalId")),
                Revision.of(request.sourceRevision()), CommonId.of(requireText(request.commonId(), "commonId")));
    }

    private static DeriveView deriveView(DeriveResult result) {
        return new DeriveView(
                result.tracker().trackerId().value(),
                result.tracker().sourceId().value(),
                LightTrackerItemView.from(result.item()));
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

    public record CreateChildRequest(String workingSetId) {}

    public record WorkingSetView(String workingSetId, boolean defaultWorkingSet, String parentWorkingSetId) {
        static WorkingSetView from(WorkingSet workingSet) {
            WorkingSetId parentId = workingSet.parentWorkingSetId();
            return new WorkingSetView(workingSet.id().value(), WorkingSet.DEFAULT_ID.equals(workingSet.id()),
                    parentId == null ? null : parentId.value());
        }
    }

    public record SpaceBindingView(String productSpaceId, String origin, String state) {
        static SpaceBindingView from(WorkingSet.SpaceBinding binding) {
            return new SpaceBindingView(binding.productSpaceId(), binding.origin().name(), binding.state().name());
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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
