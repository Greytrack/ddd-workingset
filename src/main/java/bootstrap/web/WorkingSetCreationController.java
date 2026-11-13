package bootstrap.web;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import sk.workingset.application.Caller;
import sk.workingset.application.WorkingSetCreationService;
import sk.workingset.domain.WorkingSetCreation;

@RestController
@RequestMapping("/api/working-set-creations")
public class WorkingSetCreationController {
    private final WorkingSetCreationService creations;

    public WorkingSetCreationController(WorkingSetCreationService creations) {
        this.creations = creations;
    }

    @PostMapping
    public ResponseEntity<CreationView> submit(@RequestBody WorkingSetCreation.Request request) {
        var creation = creations.submit(request, Caller.of("web"));
        return ResponseEntity.accepted().location(URI.create("/api/working-set-creations/" + creation.id()))
                .body(CreationView.from(creation));
    }

    @GetMapping("/{id}")
    public CreationView find(@PathVariable String id) {
        return CreationView.from(creations.find(id));
    }

    public record CreationView(String creationId, WorkingSetCreation.Status status, WorkingSetCreation.Stage stage,
                               WorkingSetCreation.Progress progress, List<WorkingSetCreation.Failure> failures,
                               Instant createdAt, Instant startedAt, Instant completedAt) {
        static CreationView from(WorkingSetCreation creation) {
            return new CreationView(creation.id(), creation.status(), creation.stage(), creation.progress(),
                    creation.failures(), creation.createdAt(), creation.startedAt(), creation.completedAt());
        }
    }
}
