package sk.workingset.application;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.application.port.ProductSpaceRepository;
import sk.workingset.application.port.TrackerRepository;
import sk.workingset.application.port.WorkingSetRepository;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.WorkingSet;

import sk.workingset.application.port.IdentifierGenerator;
import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.TrackerItemReadPort;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * SK 应用服务：WorkingSet 谱系、纳入、解析与读取编排。
 *
 * <p>业务事实通过应用层 Port 获取（AC-01）。
 * 业务标识由 {@link IdentifierGenerator} 以雪花 ID 产生（P1/FACT-005）。
 */
public class WorkingSetApplicationService {

    private final LightTrackerItemRepository items;
    private final Function<LocalId, TrackerItemReadPort> readPorts;
    private final IdentifierGenerator identifierGenerator;
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final ProductSpaceRepository spaces;
    private final BusinessTrackerItems businessItems;

    public WorkingSetApplicationService(LightTrackerItemRepository items,
                                        Function<LocalId, TrackerItemReadPort> readPorts,
                                        IdentifierGenerator identifierGenerator,
                                        WorkingSetRepository workingSets,
                                        TrackerRepository trackers,
                                        ProductSpaceRepository spaces,
                                        BusinessTrackerItems businessItems) {
        this.items = Objects.requireNonNull(items, "items");
        this.readPorts = Objects.requireNonNull(readPorts, "readPorts");
        this.identifierGenerator = Objects.requireNonNull(identifierGenerator, "identifierGenerator");
        this.workingSets = Objects.requireNonNull(workingSets, "workingSets");
        this.trackers = Objects.requireNonNull(trackers, "trackers");
        this.spaces = Objects.requireNonNull(spaces, "spaces");
        this.businessItems = Objects.requireNonNull(businessItems, "businessItems");
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WorkingSet findWorkingSet(WorkingSetId id) {
        return workingSets.findWorkingSet(id)
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, id.value()));
    }

    @Transactional
    public WorkingSet createChildWorkingSet(WorkingSetId parentId, WorkingSetId newId) {
        if (newId.equals(parentId)) {
            throw new BindingFailure(Reason.WorkingSetCycle, newId.value());
        }
        writable(parentId);
        if (workingSets.findAncestors(parentId).contains(newId)) {
            throw new BindingFailure(Reason.WorkingSetCycle, newId.value());
        }
        var child = WorkingSet.child(newId, parentId);
        if (!workingSets.insertWorkingSet(child)) {
            var existing = workingSets.findWorkingSet(newId);
            boolean differentParent = existing.filter(set -> !parentId.equals(set.parentWorkingSetId())).isPresent();
            var reason = differentParent ? Reason.WorkingSetParentImmutable : Reason.WorkingSetAlreadyExists;
            throw new BindingFailure(reason, newId.value());
        }
        return child;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<WorkingSetId> findChildren(WorkingSetId id) {
        findWorkingSet(id);
        return workingSets.findChildren(id);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<WorkingSetId> findAncestors(WorkingSetId id) {
        findWorkingSet(id);
        return workingSets.findAncestors(id);
    }

    @Transactional
    public WorkingSet.SpaceBinding bindSpace(WorkingSetId id, String spaceId) {
        var workingSet = writable(id);
        var space = spaces.lockProductSpace(spaceId)
                .orElseThrow(() -> new BindingFailure(Reason.ProductSpaceNotFound, spaceId));
        var binding = workingSet.bindSpace(space);
        workingSets.insertSpaceBinding(binding);
        return binding;
    }

    @Transactional
    public WorkingSet.TrackerBinding bindTrackerShared(WorkingSetId id, TrackerId trackerId) {
        var workingSet = writable(id);
        var source = directParentTracker(workingSet, trackerId);
        var binding = workingSet.bindTracker(source, classification(source), SharingMode.SHARED);
        workingSets.insertTrackerBinding(binding);
        return binding;
    }

    private WorkingSet writable(WorkingSetId id) {
        return workingSets.lockWorkingSet(id)
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, id.value()));
    }

    private Tracker directParentTracker(WorkingSet target, TrackerId id) {
        WorkingSetId parentId = target.requireParentId();
        // 非根父级锁定绑定范围，串行化解绑与新纳入。根原生 Tracker 由 Tracker 行锁保护。
        boolean boundInParent = !WorkingSet.DEFAULT_ID.equals(parentId) && writable(parentId).hasTrackerBinding(id);
        var source = trackers.lockTracker(id)
                .orElseThrow(() -> new BindingFailure(Reason.SourceTrackerNotFound, id.value()));
        target.requireDirectParent(source, boundInParent);
        return source;
    }

    private Classification classification(Tracker tracker) {
        return spaces.findClassification(tracker.classificationId())
                .orElseThrow(() -> new BindingFailure(Reason.SharingModeMismatch, tracker.classificationId()));
    }

    /** 非共享派生：创建目标 Tracker 与 Source-backed 的 LightTrackerItem，不复制业务内容。 */
    @Transactional
    public DeriveResult derive(DeriveCommand command) {
        var workingSet = writable(command.workingSetId());
        return bindTrackerDerived(workingSet, directParentTracker(workingSet, command.sourceTrackerId()), command);
    }

    @Transactional
    public DeriveResult bindTrackerDerived(String spaceId, String classificationId, DeriveCommand command) {
        var workingSet = writable(command.workingSetId());
        var source = directParentTracker(workingSet, command.sourceTrackerId());
        if (!source.productSpaceId().equals(spaceId) || !source.classificationId().equals(classificationId)) {
            throw new BindingFailure(Reason.TrackerOutsideSlot, source.trackerId().value());
        }
        return bindTrackerDerived(workingSet, source, command);
    }

    private DeriveResult bindTrackerDerived(WorkingSet workingSet, Tracker source, DeriveCommand command) {
        if (source.sharingMode() != SharingMode.UNSHARED) {
            throw new BindingFailure(Reason.SharingModeMismatch, source.classificationId());
        }
        Tracker tracker = Tracker.derived(TrackerId.of(String.valueOf(identifierGenerator.nextId())),
                source, workingSet.id());
        var binding = workingSet.bindTracker(tracker, classification(source), SharingMode.UNSHARED);
        var contentSource = resolveContentSource(source, command);
        LightTrackerItem item = LightTrackerItem.derive(
                LocalId.of(String.valueOf(identifierGenerator.nextId())),
                command.commonId(),
                tracker.trackerId(),
                workingSet.id(),
                command.sourceRevision(),
                contentSource.localId(),
                contentSource.revision());
        trackers.insertTracker(tracker);
        workingSets.insertTrackerBinding(binding);
        items.save(item);
        return new DeriveResult(tracker, item);
    }

    private ContentSource resolveContentSource(Tracker source, DeriveCommand command) {
        LocalId sourceId = command.sourceLocalId();
        if (source.isDerived()) {
            var variant = items.findById(sourceId).orElseThrow(
                    () -> new BindingFailure(Reason.SourceItemNotFound, sourceId.value()));
            if (!source.workingSetId().equals(variant.workingSetId())
                    || !source.trackerId().equals(variant.trackerId())
                    || !command.commonId().equals(variant.commonId())
                    || !command.sourceRevision().equals(variant.revision())) {
                throw new BindingFailure(Reason.SourceItemMismatch, sourceId.value());
            }
            LocalId contentId = variant.effectiveContentSource();
            if (!businessItems.lockContentSource(contentId)) {
                throw new ApplicationFailure(FailureReason.HISTORY_UNAVAILABLE, contentId.value());
            }
            if (variant.materialized() && businessItems.isDeleted(sourceId)) {
                throw new ApplicationFailure(FailureReason.DELETED, sourceId.value());
            }
            return new ContentSource(contentId, variant.effectiveContentRevision());
        }

        var sourceItem = businessItems.findIdentity(sourceId).orElseThrow(
                () -> new BindingFailure(Reason.SourceItemNotFound, sourceId.value()));
        if (!source.trackerId().equals(sourceItem.trackerId()) || !command.commonId().equals(sourceItem.commonId())) {
            throw new BindingFailure(Reason.SourceItemMismatch, sourceId.value());
        }
        return new ContentSource(sourceId, command.sourceRevision());
    }

    private record ContentSource(LocalId localId, Revision revision) {}

    /** 按工作集 + 变体身份解析；未命中 → NotFound；存在但不在该工作集 → NotIncluded。 */
    public LightTrackerItem resolve(WorkingSetId workingSetId, LocalId localId) {
        Objects.requireNonNull(workingSetId, "workingSetId");
        Objects.requireNonNull(localId, "localId");
        LightTrackerItem item = items.findById(localId)
                .orElseThrow(() -> new ApplicationFailure(
                        FailureReason.NOT_FOUND, "item not found: " + localId.value()));
        if (!item.workingSetId().equals(workingSetId)) {
            throw new ApplicationFailure(
                    FailureReason.NOT_INCLUDED,
                    "item " + localId.value() + " is not included in working set " + workingSetId.value());
        }
        if (item.materialized() && businessItems.isDeleted(localId)) {
            throw new ApplicationFailure(FailureReason.DELETED, localId.value());
        }
        return item;
    }

    /**
     * 读取有效内容：Source-backed 沿 {@code sourceLocalId} 直接定位实际内容拥有者并读其
     * {@code sourceRevision}；Local-backed 读本地 {@code revision}。
     */
    public ReadResult read(WorkingSetId workingSetId, LocalId localId, Caller caller) {
        Objects.requireNonNull(caller, "caller");
        LightTrackerItem item = resolve(workingSetId, localId);
        LocalId contentLocalId = item.effectiveContentSource();
        Revision contentRevision = item.effectiveContentRevision();

        TrackerItemReadPort port = readPorts.apply(contentLocalId);
        if (!port.authorize(Operation.READ, caller)) {
            throw new UnsupportedOperationException("authorization is not enforced in S1");
        }
        TrackerItemContent content = item.materialized() ? port.readAt(contentRevision)
                : port.readInheritedAt(contentRevision);
        return new ReadResult(content, item.materialized(), contentLocalId, contentRevision);
    }
}
