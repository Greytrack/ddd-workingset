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
        DeriveResult result = applicationService.derive(new DeriveCommand(
                WorkingSetId.of(workingSetId),
                TrackerId.of(requireText(request.sourceTrackerId(), "sourceTrackerId")),
                LocalId.of(requireText(request.sourceLocalId(), "sourceLocalId")),
                Revision.of(request.sourceRevision()),
                CommonId.of(requireText(request.commonId(), "commonId"))));
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

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
