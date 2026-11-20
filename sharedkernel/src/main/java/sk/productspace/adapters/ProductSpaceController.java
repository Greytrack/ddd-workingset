package sk.productspace.adapters;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import sk.common.error.DomainException;
import sk.productspace.application.command.ProductSpaceCommandService;
import sk.productspace.domain.ProductSpace;
import sk.workingset.error.WorkingsetResultCode;

/**
 * 产品空间的 HTTP 入口：创建与删除。
 * 删除的引用完整性由 {@link ProductSpaceCommandService} 承担。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductSpaceController {
    private final ProductSpaceCommandService commands;

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
     * 删除产品空间；须在所有工作集无有效 SpaceBinding 且空间内无 Tracker（T2 §4.3）。
     */
    @DeleteMapping({"/product-spaces/{spaceId}", "/worksets/1/product-spaces/{spaceId}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String spaceId) {
        commands.delete(spaceId);
    }

    public record CreateProductSpaceRequest(String name) {
    }

    public record ProductSpaceView(String productSpaceId, String name) {
    }
}
