package sk.productspace.adapters;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sk.common.error.DomainException;
import sk.productspace.application.command.ProductSpaceCommandService;
import sk.productspace.domain.ProductSpace;
import sk.tracker.domain.TrackerId;
import sk.workingset.application.command.WorkingSetCommandService;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.error.WorkingsetResultCode;

/**
 * 产品空间的 HTTP 入口：创建、删除与空间内 Tracker 展示。
 * 删除的引用完整性由 {@link ProductSpaceCommandService} 承担。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductSpaceController {
    private final ProductSpaceCommandService commands;
    private final WorkingSetCommandService workingSets;

    /**
     * 创建产品空间；名称去首尾空白后不得为空。
     */
    @PostMapping({"/product-spaces", "/worksets/{workingSetId}/product-spaces"})
    @ResponseStatus(HttpStatus.CREATED)
    public ProductSpaceView create(@RequestBody CreateProductSpaceRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name must not be blank");
        }
        ProductSpace space = commands.create(request.name());
        return new ProductSpaceView(space.id(), space.name());
    }

    /**
     * 默认工作集受理空产品空间删除。
     */
    @DeleteMapping("/worksets/1/product-spaces/{spaceId}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void delete(@PathVariable String spaceId) {
        commands.delete(spaceId);
    }

    /**
     * 修改 Tracker 在指定工作集、产品空间中的展示状态。
     */
    @PatchMapping("/worksets/{workingSetId}/product-spaces/{spaceId}/trackers/{trackerId}/visibility")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setTrackerShown(@PathVariable String workingSetId, @PathVariable long spaceId,
                                @PathVariable String trackerId, @RequestBody TrackerVisibilityRequest request) {
        if (request.shown() == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "shown is required");
        }
        workingSets.setTrackerShown(WorkingSetId.of(workingSetId), spaceId, TrackerId.of(trackerId), request.shown());
    }

    public record TrackerVisibilityRequest(Boolean shown) {
    }

    public record CreateProductSpaceRequest(String name) {
    }

    public record ProductSpaceView(String productSpaceId, String name) {
    }
}
