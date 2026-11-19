package sk.productspace.application.command;

import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.repository.ProductSpaceRepository;
import sk.workingset.application.query.provider.TrackerQueryProvider;
import sk.workingset.application.command.WorkingSetCommandService;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.WorkingSetId;
import sk.workingset.domain.repository.WorkingSetRepository;

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
                .orElseThrow(() -> new BindingFailure(Reason.ProductSpaceNotFound, id));
        List<String> bound = queries.findWorkingSetsBoundToSpace(id).stream().map(String::valueOf).toList();
        if (!bound.isEmpty()) {
            throw new BindingFailure(Reason.SpaceStillBoundInWorkingSet, id, Map.of("workingSetIds", bound));
        }
        if (trackerQueries.hasTrackersInSpace(id)) {
            throw new BindingFailure(Reason.SpaceStillHasTrackers, id);
        }
        spaces.delete(space);
    }
}
