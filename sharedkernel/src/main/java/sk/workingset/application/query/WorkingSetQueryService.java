package sk.workingset.application.query;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;
import sk.workingset.application.query.model.BindingsView;
import sk.workingset.application.query.model.ClassificationSummary;
import sk.workingset.application.query.model.TrackerSummary;
import sk.workingset.application.query.model.WorkingSetSummary;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.error.BindingResCode;

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
    public WorkingSetSummary find(WorkingSetId id) {
        requireReady(id);
        return query.find(id.numericValue())
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, id.value()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Long> findChildren(WorkingSetId id) {
        find(id);
        return query.findChildren(id.numericValue());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Long> findAncestors(WorkingSetId id) {
        find(id);
        List<Long> ancestors = query.findAncestors(id.numericValue());
        if (!ancestors.isEmpty() && !WorkingSet.DEFAULT_ID.numericValue().equals(ancestors.getLast())) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_CYCLE, id.value());
        }
        return ancestors;
    }

    @Transactional(readOnly = true)
    public List<TrackerSummary> findTrackers(WorkingSetId id) {
        find(id);
        return query.findTrackers(id.numericValue());
    }

    @Transactional(readOnly = true)
    public List<ClassificationSummary> findClassifications() {
        return query.findClassifications();
    }

    @Transactional(readOnly = true)
    public BindingsView findBindings(WorkingSetId id) {
        find(id);
        return query.findBindings(id.numericValue());
    }

    private void requireReady(WorkingSetId id) {
        Objects.requireNonNull(id, "workingSetId");
        if (query.isPreparing(id.numericValue())) {
            throw DomainException.withMessage(FailureReason.PREPARING, id.value());
        }
    }
}
