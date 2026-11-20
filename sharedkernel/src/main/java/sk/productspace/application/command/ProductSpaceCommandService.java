package sk.productspace.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.repository.ProductSpaceRepository;
import sk.workingset.application.command.WorkingSetCommandService;
import sk.workingset.application.query.provider.TrackerQueryProvider;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.domain.workingset.WorkingSetRepository;
import sk.workingset.error.BindingResCode;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProductSpaceCommandService {
    private final ProductSpaceRepository spaces;
    private final WorkingSetCommandService memberships;
    private final WorkingSetRepository workingSets;
    private final WorkingSetQueryProvider queries;
    private final TrackerQueryProvider trackerQueries;
    private final IdentifierGenerator ids;

    @Transactional
    public ProductSpace create(String name) {
        ProductSpace space = new ProductSpace(ids.nextId(null).longValue(), name);
        spaces.save(space);
        memberships.initializeProductSpaceMembership(space);
        return space;
    }

    @Transactional
    public void delete(String id) {
        ProductSpace space = spaces.findById(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.PRODUCT_SPACE_NOT_FOUND, id));
        List<String> bound = queries.findWorkingSetsBoundToSpace(id).stream().map(String::valueOf).toList();
        if (!bound.isEmpty()) {
            throw DomainException.withMessage(BindingResCode.SPACE_STILL_BOUND_IN_WORKING_SET, id, Map.of("workingSetIds", bound));
        }
        if (trackerQueries.hasTrackersInSpace(id)) {
            throw DomainException.withMessage(BindingResCode.SPACE_STILL_HAS_TRACKERS, id);
        }
        spaces.delete(space);
    }
}
