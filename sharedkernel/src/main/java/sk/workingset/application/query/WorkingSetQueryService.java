package sk.workingset.application.query;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.Caller;
import sk.workingset.application.FailureReason;
import sk.workingset.application.Operation;
import sk.workingset.application.ReadResult;
import sk.workingset.application.TrackerItemContent;
import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.application.port.TrackerItemReadPort;
import sk.workingset.application.query.model.BindingsView;
import sk.workingset.application.query.model.ClassificationSummary;
import sk.workingset.application.query.model.TrackerSummary;
import sk.workingset.application.query.model.WorkingSetSummary;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;

/** Working Set 查询侧应用服务：不重建聚合，不执行写操作。 */
public class WorkingSetQueryService {
    private final WorkingSetQueryProvider query;
    private final LightTrackerItemRepository items;
    private final Function<LocalId, TrackerItemReadPort> readPorts;
    private final BusinessTrackerItems businessItems;

    public WorkingSetQueryService(WorkingSetQueryProvider query, LightTrackerItemRepository items,
                                  Function<LocalId, TrackerItemReadPort> readPorts,
                                  BusinessTrackerItems businessItems) {
        this.query = Objects.requireNonNull(query, "query");
        this.items = Objects.requireNonNull(items, "items");
        this.readPorts = Objects.requireNonNull(readPorts, "readPorts");
        this.businessItems = Objects.requireNonNull(businessItems, "businessItems");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WorkingSetSummary find(WorkingSetId id) {
        requireReady(id);
        return query.find(id.numericValue())
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, id.value()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Long> findChildren(WorkingSetId id) {
        find(id);
        return query.findChildren(id.numericValue());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<Long> findAncestors(WorkingSetId id) {
        find(id);
        var ancestors = query.findAncestors(id.numericValue());
        if (!ancestors.isEmpty() && !WorkingSet.DEFAULT_ID.numericValue().equals(ancestors.getLast())) {
            throw new BindingFailure(Reason.WorkingSetCycle, id.value());
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

    @Transactional(readOnly = true)
    public List<IndexedItem> findItems(WorkingSetId workingSetId, TrackerId trackerId) {
        find(workingSetId);
        if (!query.hasTrackerBinding(workingSetId.numericValue(), trackerId.numericValue())) {
            throw new ApplicationFailure(FailureReason.NOT_INCLUDED, trackerId.value());
        }
        return items.findInTracker(trackerId).stream()
                .filter(item -> !businessItems.isDeleted(item.effectiveContentSource()))
                .map(item -> new IndexedItem(item,
                        businessItems.presentation(item.effectiveContentSource()).orElse(null)))
                .toList();
    }

    public record IndexedItem(LightTrackerItem item, BusinessTrackerItems.Presentation presentation) {}

    /** 按工作集 + 变体身份解析；未命中 → NotFound；存在但不在该工作集 → NotIncluded。 */
    public LightTrackerItem resolve(WorkingSetId workingSetId, LocalId localId) {
        Objects.requireNonNull(localId, "localId");
        requireReady(workingSetId);
        LightTrackerItem item = items.findById(localId)
                .orElseThrow(() -> new ApplicationFailure(
                        FailureReason.NOT_FOUND, "item not found: " + localId.value()));
        if (!item.workingSetId().equals(workingSetId)) {
            throw new ApplicationFailure(FailureReason.NOT_INCLUDED,
                    "item " + localId.value() + " is not included in working set " + workingSetId.value());
        }
        if (item.materialized() && businessItems.isDeleted(localId)) {
            throw new ApplicationFailure(FailureReason.DELETED, localId.value());
        }
        return item;
    }

    /** 读取有效内容：Source-backed 沿来源定位；Local-backed 读本地。 */
    public ReadResult read(WorkingSetId workingSetId, LocalId localId, Caller caller) {
        Objects.requireNonNull(caller, "caller");
        LightTrackerItem item = resolve(workingSetId, localId);
        LocalId contentLocalId = item.effectiveContentSource();
        Revision contentRevision = item.effectiveContentRevision();
        TrackerItemReadPort port = readPorts.apply(contentLocalId);
        if (!port.authorize(Operation.READ, caller)) {
            throw new ApplicationFailure(FailureReason.DENIED, contentLocalId.value());
        }
        TrackerItemContent content = item.materialized() ? port.readAt(contentRevision)
                : port.readInheritedAt(contentRevision);
        return new ReadResult(content, item.materialized(), contentLocalId, contentRevision);
    }

    private void requireReady(WorkingSetId id) {
        Objects.requireNonNull(id, "workingSetId");
        if (query.isPreparing(id.numericValue())) {
            throw new ApplicationFailure(FailureReason.PREPARING, id.value());
        }
    }
}
