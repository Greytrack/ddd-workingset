package bootstrap.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import sk.workingset.application.WorkingSetDeletionService;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

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

    @DeleteMapping("/worksets/{workingSetId}/trackers/{trackerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unbindTracker(@PathVariable String workingSetId, @PathVariable String trackerId) {
        deletions.unbindTracker(WorkingSetId.of(workingSetId), TrackerId.of(trackerId));
    }

    @DeleteMapping("/worksets/{workingSetId}/spaces/{spaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeSpace(@PathVariable String workingSetId, @PathVariable String spaceId) {
        deletions.removeSpace(WorkingSetId.of(workingSetId), spaceId);
    }

    @DeleteMapping("/trackers/{trackerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTracker(@PathVariable String trackerId) {
        deletions.deleteTracker(TrackerId.of(trackerId));
    }

    @DeleteMapping("/worksets/1/product-spaces/{spaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProductSpace(@PathVariable String spaceId) {
        deletions.deleteProductSpace(spaceId);
    }

    @DeleteMapping("/worksets/{workingSetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteWorkingSet(@PathVariable String workingSetId) {
        deletions.deleteWorkingSet(WorkingSetId.of(workingSetId));
    }
}
