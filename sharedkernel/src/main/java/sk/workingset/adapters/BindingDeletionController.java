package sk.workingset.adapters;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.command.WorkingSetDeletionService;
import sk.workingset.domain.workingset.WorkingSetId;

/**
 * 绑定解除与聚合删除的 HTTP 入口。
 *
 * <p>把 URL 映射到 {@link WorkingSetDeletionService}；删除顺序与拒绝语义由应用服务承担，
 * 控制器不判断业务规则。
 */
@RestController
@RequestMapping("/api")
public class BindingDeletionController {
    private final WorkingSetDeletionService deletions;

    public BindingDeletionController(WorkingSetDeletionService deletions) {
        this.deletions = deletions;
    }

    /**
     * 解除工作集与 Tracker 的绑定；Shared 仅解绑、聚合保留，非共享须通过三项检查后删除（T2 §4.2）。
     */
    @DeleteMapping("/worksets/{workingSetId}/trackers/{trackerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbindTracker(@PathVariable String workingSetId, @PathVariable String trackerId) {
        deletions.unbindTracker(WorkingSetId.of(workingSetId), TrackerId.of(trackerId));
    }

    /**
     * 从工作集移除产品空间；须先解除该空间下的全部 TrackerBinding（SB-4）。
     */
    @DeleteMapping("/worksets/{workingSetId}/spaces/{spaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSpace(@PathVariable String workingSetId, @PathVariable String spaceId) {
        deletions.removeSpace(WorkingSetId.of(workingSetId), spaceId);
    }

    /**
     * 删除工作集；默认工作集不可删，须先合法解除全部 TrackerBinding 与 SpaceBinding（T2 K3）。
     */
    @DeleteMapping("/worksets/{workingSetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkingSet(@PathVariable String workingSetId) {
        deletions.deleteWorkingSet(WorkingSetId.of(workingSetId));
    }
}
