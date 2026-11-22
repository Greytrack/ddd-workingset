package com.airedgesoft.sharekernel.workingset.application.command;

import com.airedgesoft.lib.execption.DomainException;
import com.airedgesoft.sharekernel.workingset.application.query.provider.TrackerQueryProvider;
import com.airedgesoft.sharekernel.workingset.application.query.provider.WorkingSetQueryProvider;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetId;
import com.airedgesoft.sharekernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharekernel.workingset.error.BindingResCode;
import lombok.RequiredArgsConstructor;
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

    /**
     * 校验引用后仅删除 WorkingSet 聚合。
     */
    @Transactional
    public void deleteWorkingSet(WorkingSetId id) {
        WorkingSet.requireDeletable(id);
        WorkingSet workingSet = workingSets.findById(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, id.value()));
        assertNoChildren(id);
        if (queries.hasBindings(id.numericValue())) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_STILL_BOUND, id.value());
        }
        if (trackerQueries.hasOwnedTrackers(id)) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_HAS_TRACKERS, id.value());
        }
        workingSets.delete(workingSet);
    }

    private void assertNoChildren(WorkingSetId id) {
        List<String> children = queries.findChildren(id.numericValue()).stream().map(WorkingSetId::of).map(WorkingSetId::value).toList();
        if (!children.isEmpty()) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_HAS_CHILDREN, id.value(), Map.of("childWorkingSetIds", children));
        }
    }
}
