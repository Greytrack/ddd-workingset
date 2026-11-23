package com.airedgesoft.sharedkernel.workingset.application.query;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.application.query.model.ClassificationSummary;
import com.airedgesoft.sharedkernel.workingset.application.query.model.TrackerSummary;
import com.airedgesoft.sharedkernel.workingset.application.query.model.WorkingSetBindings;
import com.airedgesoft.sharedkernel.workingset.application.query.model.WorkingSetSummary;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.WorkingSetQueryProvider;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Working Set 查询侧应用服务：读模型优先，不重建聚合，不执行写操作。
 */
@Service
@RequiredArgsConstructor
public class WorkingSetQueryService {
    private final WorkingSetQueryProvider query;


    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WorkingSetSummary find(Long id) {
        requireReady(id);
        return query.find(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(id)));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Long> findChildren(Long id) {
        find(id);
        return query.findChildren(id);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Long> findAncestors(Long id) {
        find(id);
        List<Long> ancestors = query.findAncestors(id);
        if (!ancestors.isEmpty() && !WorkingSet.DEFAULT_ID.equals(ancestors.getLast())) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_CYCLE, Long.toString(id));
        }
        return ancestors;
    }

    @Transactional(readOnly = true)
    public List<TrackerSummary> findTrackers(Long id) {
        find(id);
        return query.findTrackers(id);
    }

    @Transactional(readOnly = true)
    public List<ClassificationSummary> findClassifications() {
        return query.findClassifications();
    }

    @Transactional(readOnly = true)
    public WorkingSetBindings findBindings(Long id) {
        find(id);
        return query.findBindings(id);
    }

    private void requireReady(Long id) {
        Objects.requireNonNull(id, "workingSetId");
        if (query.isPreparing(id)) {
            throw DomainException.withMessage(FailureReason.PREPARING, Long.toString(id));
        }
    }
}
