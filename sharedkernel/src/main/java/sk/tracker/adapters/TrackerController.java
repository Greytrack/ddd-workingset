package sk.tracker.adapters;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sk.common.error.DomainException;
import sk.tracker.application.command.TrackerCommandService;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.error.WorkingsetResultCode;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TrackerController {
    private final TrackerCommandService commands;

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, field + " must not be blank");
        }
        return value;
    }

    @PostMapping("/worksets/{workingSetId}/trackers")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackerView create(@PathVariable String workingSetId, @RequestBody CreateTrackerRequest request) {
        SharingMode mode = SharingMode.valueOf(required(request.sharingMode(), "sharingMode"));
        return TrackerView.from(commands.create(WorkingSetId.of(workingSetId),
                required(request.productSpaceId(), "productSpaceId"), required(request.name(), "name"),
                required(request.classificationId(), "classificationId"), mode));
    }

    @PatchMapping("/trackers/{trackerId}")
    public TrackerView rename(@PathVariable String trackerId, @RequestBody RenameTrackerRequest request) {
        return TrackerView.from(commands.rename(TrackerId.of(trackerId), request.name()));
    }

    @DeleteMapping("/trackers/{trackerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String trackerId) {
        commands.delete(TrackerId.of(trackerId));
    }

    public record CreateTrackerRequest(String productSpaceId, String name, String classificationId,
                                       String sharingMode) {
    }

    public record RenameTrackerRequest(String name) {
    }

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
}
