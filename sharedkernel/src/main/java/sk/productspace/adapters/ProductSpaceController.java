package sk.productspace.adapters;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import sk.common.error.DomainException;
import sk.productspace.application.command.ProductSpaceCommandService;
import sk.productspace.domain.ProductSpace;
import sk.workingset.error.WorkingsetResultCode;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProductSpaceController {
    private final ProductSpaceCommandService commands;

    @PostMapping({"/product-spaces", "/worksets/{workingSetId}/product-spaces"})
    @ResponseStatus(HttpStatus.CREATED)
    public ProductSpaceView create(@RequestBody CreateProductSpaceRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name must not be blank");
        }
        ProductSpace space = commands.create(request.name());
        return new ProductSpaceView(space.id(), space.name());
    }

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
