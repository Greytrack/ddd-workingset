package alm.requirement.adapters;

import alm.requirement.application.command.RequirementCommandService;
import alm.requirement.application.command.RequirementLifecycleService;
import alm.requirement.application.query.RequirementQueryService;
import alm.requirement.application.query.model.RequirementDetail;
import alm.requirement.domain.Requirement;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sk.common.error.DomainException;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.LockVersion;
import sk.workingset.domain.Revision;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.error.WorkingsetResultCode;

import java.util.List;
import java.util.Objects;

/**
 * Requirement 的最小 REST 入口：创建、普通修改标题、按 LocalId 查询。
 *
 * <p>创建请求不携带 CommonId：身份由应用服务按工作集分配（T6-C）。
 */
@RestController
@RequestMapping("/api/requirements")
public class RequirementController {

    private final RequirementCommandService commands;
    private final RequirementQueryService queries;
    private final RequirementLifecycleService lifecycle;

    public RequirementController(RequirementCommandService commands, RequirementQueryService queries,
                                 RequirementLifecycleService lifecycle) {
        this.commands = commands;
        this.queries = queries;
        this.lifecycle = lifecycle;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, name + " must not be blank");
        }
        return value;
    }

    /**
     * 创建需求；请求不携带 CommonId，身份由应用服务按工作集分配（T6-C）。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequirementView create(@RequestBody CreateRequirementRequest request) {
        String requested = request.workingSetId();
        WorkingSetId requestedWorkingSetId = requested == null || requested.isBlank()
                ? null : WorkingSetId.of(requested);
        Requirement created = commands.create(
                TrackerId.of(requireText(request.trackerId(), "trackerId")),
                requestedWorkingSetId,
                requireText(request.title(), "title"),
                Objects.requireNonNull(request.description(), "description"),
                Revision.of(request.revision()));
        return RequirementView.from(created);
    }

    /**
     * ALM 场景查询需求列表；工作集只通过 LightTrackerItem 列表与本接口组合展示。
     */
    @GetMapping
    public List<RequirementView> list(@RequestParam String trackerId,
                                      @RequestParam String workingSetId) {
        return queries.findByTracker(WorkingSetId.of(workingSetId), TrackerId.of(trackerId)).stream()
                .map(RequirementView::from).toList();
    }

    /**
     * 修改需求标题，需携带期望乐观锁版本。
     */
    @PutMapping("/{localId}/title")
    public RequirementView changeTitle(@PathVariable("localId") String localId,
                                       @RequestBody ChangeTitleRequest request) {
        Requirement changed = commands.changeTitle(
                LocalId.of(localId),
                requireText(request.title(), "title"),
                LockVersion.of(request.expectedLockVersion()));
        return RequirementView.from(changed);
    }

    /**
     * 编辑单个需求的标题和描述。
     */
    @PutMapping("/{localId}")
    public RequirementView changeContent(@PathVariable("localId") String localId,
                                         @RequestBody ChangeRequirementRequest request) {
        Requirement changed = commands.changeContent(
                LocalId.of(localId),
                requireText(request.title(), "title"),
                Objects.requireNonNull(request.description(), "description"),
                LockVersion.of(request.expectedLockVersion()));
        return RequirementView.from(changed);
    }

    /**
     * REST 风格的单个需求软删除；旧入口 POST /{localId}/delete 保持兼容。
     */
    @DeleteMapping("/{localId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOne(@PathVariable String localId) {
        commands.delete(LocalId.of(localId));
    }

    /**
     * 软删除需求（兼容旧 POST /{localId}/delete 入口）。
     */
    @PostMapping("/{localId}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String localId) {
        commands.delete(LocalId.of(localId));
    }

    /**
     * 永久删除的 guard 入口；实际永久删除由应用服务编排（D14/D7）。
     */
    @PostMapping("/{localId}/delete2")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete2(@PathVariable String localId) {
        lifecycle.delete2(LocalId.of(localId));
    }

    /**
     * 恢复软删除的需求。
     */
    @PostMapping("/{localId}/undelete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undelete(@PathVariable String localId) {
        commands.undelete(LocalId.of(localId));
    }

    /**
     * 按 LocalId 查询需求详情。
     */
    @GetMapping("/{localId}")
    public RequirementView find(@PathVariable("localId") String localId) {
        RequirementDetail detail = queries.find(LocalId.of(localId));
        return RequirementView.from(detail);
    }
}
