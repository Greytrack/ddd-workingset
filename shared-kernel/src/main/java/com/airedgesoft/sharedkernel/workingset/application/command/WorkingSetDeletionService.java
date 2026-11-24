package com.airedgesoft.sharedkernel.workingset.application.command;


import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.businessframework.application.context.AuthorizationScope;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.TrackerQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.WorkingSetQueryProvider;
import com.airedgesoft.sharedkernel.workingset.domain.event.WorkingSetDeleted;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 删除无子级、无绑定且不再拥有 Tracker 的工作集。
 */
@Service
@RequiredArgsConstructor
public class WorkingSetDeletionService {
    private final WorkingSetRepository workingSets;
    private final WorkingSetQueryProvider queries;
    private final TrackerQueryProvider trackerQueries;
    private final ApplicationEventPublisher events;

    /**
     * 校验引用后仅删除 WorkingSet 聚合。
     */
    @Transactional
    public void deleteWorkingSet(Long id) {
        WorkingSet.requireDeletable(id);
        WorkingSet workingSet = workingSets.findByIdForUpdate(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(id)));
        assertNoChildren(id);
        if (queries.hasBindings(id)) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_STILL_BOUND, Long.toString(id));
        }
        if (trackerQueries.hasOwnedTrackers(id)) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_HAS_TRACKERS, Long.toString(id));
        }
        WorkingSetDeleted deletedEvent = workingSet.delete(Long.toString(AuthorizationScope.currentUserId()));
        workingSets.save(workingSet);
        events.publishEvent(deletedEvent);
    }

    private void assertNoChildren(Long id) {
        List<String> children = queries.findActiveChildren(id).stream().map(Object::toString).toList();
        if (!children.isEmpty()) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_HAS_CHILDREN, Long.toString(id), Map.of("childWorkingSetIds", children));
        }
    }
}
