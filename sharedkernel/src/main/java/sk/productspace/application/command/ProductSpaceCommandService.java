package sk.productspace.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.event.ProductSpaceCreatedEvent;
import sk.productspace.domain.repository.ProductSpaceRepository;
import sk.workingset.application.query.provider.TrackerQueryProvider;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.error.BindingResCode;

import java.util.List;
import java.util.Map;

/**
 * 产品空间命令服务：创建与删除；删除前审查所有工作集的绑定与空间内 Tracker。
 */
@Service
@RequiredArgsConstructor
public class ProductSpaceCommandService {
    private final ProductSpaceRepository spaces;
    private final ApplicationEventPublisher events;
    private final WorkingSetQueryProvider queries;
    private final TrackerQueryProvider trackerQueries;
    private final IdentifierGenerator ids;

    /**
     * 创建产品空间并发布创建事件。
     */
    @Transactional
    public ProductSpace create(String name) {
        ProductSpace space = new ProductSpace(ids.nextId(null).longValue(), name);
        spaces.save(space);
        events.publishEvent(new ProductSpaceCreatedEvent(space.numericId()));
        return space;
    }

    /**
     * 删除产品空间；须无工作集绑定且空间内无 Tracker。
     */
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
