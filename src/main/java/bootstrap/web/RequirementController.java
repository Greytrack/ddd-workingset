package bootstrap.web;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import alm.requirement.application.RequirementApplicationService;
import alm.requirement.application.RequirementLifecycleService;
import alm.requirement.domain.Requirement;
import alm.requirement.domain.RequirementRepository;
import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;

/**
 * Requirement 的最小 REST 入口：创建、普通修改标题、按 LocalId 查询。
 *
 * <p>创建请求不携带 CommonId：身份由应用服务按工作集分配（T6-C）。
 */
@RestController
@RequestMapping("/api/requirements")
public class RequirementController {

    private final RequirementApplicationService applicationService;
    private final RequirementRepository repository;
    private final RequirementLifecycleService lifecycle;

    public RequirementController(RequirementApplicationService applicationService,
                                 RequirementRepository repository,
                                 RequirementLifecycleService lifecycle) {
        this.applicationService = applicationService;
        this.repository = repository;
        this.lifecycle = lifecycle;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementView create(@RequestBody CreateRequirementRequest request) {
        String requested = request.workingSetId();
        WorkingSetId requestedWorkingSetId = requested == null || requested.isBlank()
                ? null : WorkingSetId.of(requested);
        Requirement created = applicationService.create(
                TrackerId.of(requireText(request.trackerId(), "trackerId")),
                requestedWorkingSetId,
                requireText(request.title(), "title"),
                Objects.requireNonNull(request.description(), "description"),
                Revision.of(request.revision()));
        return RequirementView.from(created);
    }

    @PutMapping("/{localId}/title")
    public RequirementView changeTitle(@PathVariable("localId") String localId,
                                       @RequestBody ChangeTitleRequest request) {
        Requirement changed = applicationService.changeTitle(
                LocalId.of(localId),
                requireText(request.title(), "title"),
                LockVersion.of(request.expectedLockVersion()));
        return RequirementView.from(changed);
    }

    @PostMapping("/{localId}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String localId) {
        applicationService.delete(LocalId.of(localId));
    }

    @PostMapping("/{localId}/delete2")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete2(@PathVariable String localId) {
        lifecycle.delete2(LocalId.of(localId));
    }

    @PostMapping("/{localId}/undelete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undelete(@PathVariable String localId) {
        applicationService.undelete(LocalId.of(localId));
    }

    @GetMapping("/{localId}")
    public RequirementView find(@PathVariable("localId") String localId) {
        return repository.findById(LocalId.of(localId))
                .map(item -> {
                    if (item.deleted()) {
                        throw new ApplicationFailure(FailureReason.DELETED, item.localId().value());
                    }
                    return RequirementView.from(item);
                })
                .orElseThrow(() -> new ApplicationFailure(
                        FailureReason.NOT_FOUND, "requirement not found: " + localId));
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
