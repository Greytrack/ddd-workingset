package bootstrap.web;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sk.workingset.application.Caller;
import sk.workingset.application.WorkingSetCreationService;
import sk.workingset.domain.WorkingSetId;

/** T7-G：对已有子工作集批量派生直接父 Tracker；任务复用 working-set-creations 轮询。 */
@RestController
@RequestMapping("/api/worksets")
public class WorkingSetExtensionController {
    private static final int MAX_REQUEST_ID = 128;
    private final WorkingSetCreationService creations;

    public WorkingSetExtensionController(WorkingSetCreationService creations) {
        this.creations = creations;
    }

    @PostMapping("/{target}/extensions")
    public ResponseEntity<WorkingSetCreationController.CreationView> extend(
            @PathVariable String target, @RequestBody ExtensionRequest request) {
        if (request.requestId() == null || request.requestId().isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        if (request.requestId().length() > MAX_REQUEST_ID) {
            throw new IllegalArgumentException("requestId must be at most " + MAX_REQUEST_ID + " characters");
        }
        var creation = creations.submitExtension(WorkingSetId.of(target), request.requestId(),
                request.trackerIds() == null ? List.of() : request.trackerIds(), Caller.of("web"));
        return ResponseEntity.accepted()
                .location(URI.create("/api/working-set-creations/" + creation.id()))
                .body(WorkingSetCreationController.CreationView.from(creation));
    }

    public record ExtensionRequest(String requestId, List<String> trackerIds) {}
}
