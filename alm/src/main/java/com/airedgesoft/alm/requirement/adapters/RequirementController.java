package com.airedgesoft.alm.requirement.adapters;


import com.airedgesoft.alm.requirement.application.command.RequirementCommandService;
import com.airedgesoft.alm.requirement.application.command.RequirementLifecycleService;
import com.airedgesoft.alm.requirement.application.query.RequirementQueryService;
import com.airedgesoft.alm.requirement.application.query.model.RequirementDetail;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.workingset.error.WorkingsetResultCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
        Long requested = request.workingSetId();
        Long requestedWorkingSetId = requested == null
                ? null : requested;
        Requirement created = commands.create(
                Objects.requireNonNull(request.trackerId(), "trackerId"),
                requestedWorkingSetId,
                requireText(request.title(), "title"),
                Objects.requireNonNull(request.description(), "description"),
                (request.revision() == null ? 0 : request.revision()));
        return RequirementView.from(created);
    }

    /**
     * ALM 场景查询需求列表；工作集只通过 LightTrackerItem 列表与本接口组合展示。
     */
    @GetMapping
    public List<RequirementView> list(@RequestParam Long trackerId,
                                      @RequestParam Long workingSetId) {
        return queries.findByTracker(workingSetId, trackerId).stream()
                .map(RequirementView::from).toList();
    }

    /**
     * 修改需求标题，需携带期望乐观锁版本。
     */
    @PutMapping("/{localId}/title")
    public RequirementView changeTitle(@PathVariable("localId") Long localId,
                                       @RequestBody ChangeTitleRequest request) {
        Requirement changed = commands.changeTitle(
                localId,
                requireText(request.title(), "title"),
                request.expectedLockVersion());
        return RequirementView.from(changed);
    }

    /**
     * 编辑单个需求的标题和描述。
     */
    @PutMapping("/{localId}")
    public RequirementView changeContent(@PathVariable("localId") Long localId,
                                         @RequestBody ChangeRequirementRequest request) {
        Requirement changed = commands.changeContent(
                localId,
                requireText(request.title(), "title"),
                Objects.requireNonNull(request.description(), "description"),
                request.expectedLockVersion());
        return RequirementView.from(changed);
    }

    /**
     * REST 风格的单个需求软删除；旧入口 POST /{localId}/delete 保持兼容。
     */
    @DeleteMapping("/{localId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOne(@PathVariable Long localId) {
        commands.delete(localId);
    }

    /**
     * 软删除需求（兼容旧 POST /{localId}/delete 入口）。
     */
    @PostMapping("/{localId}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long localId) {
        commands.delete(localId);
    }

    /**
     * 永久删除的 guard 入口；实际永久删除由应用服务编排（D14/D7）。
     */
    @PostMapping("/{localId}/delete2")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete2(@PathVariable Long localId) {
        lifecycle.delete2(localId);
    }

    /**
     * 恢复软删除的需求。
     */
    @PostMapping("/{localId}/undelete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undelete(@PathVariable Long localId) {
        commands.undelete(localId);
    }

    /**
     * 按 LocalId 查询需求详情。
     */
    @GetMapping("/{localId}")
    public RequirementView find(@PathVariable("localId") Long localId) {
        RequirementDetail detail = queries.find(localId);
        return RequirementView.from(detail);
    }
}
