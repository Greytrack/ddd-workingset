package sk.workingset.application;

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
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/**
 * SK 应用服务（首切片）：非共享派生、解析与读取编排。
 *
 * <p>SK 只依赖 {@link TrackerItemReadPort} 与自身仓储；不引用 ALM 的任何类型（AC-01）。
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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public WorkingSet listBindings(WorkingSetId id) {
        return findWorkingSet(id);
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
        var source = sourceTracker(trackerId);
        workingSet.requireDirectUpstream(source);
        var binding = workingSet.bindTracker(source, classification(source), SharingMode.SHARED);
        workingSets.insertTrackerBinding(binding);
        return binding;
    }

    private WorkingSet writable(WorkingSetId id) {
        return workingSets.lockWorkingSet(id)
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, id.value()));
    }

    private Tracker sourceTracker(TrackerId id) {
        return trackers.lockTracker(id)
                .orElseThrow(() -> new BindingFailure(Reason.SourceTrackerNotFound, id.value()));
    }

    private Classification classification(Tracker tracker) {
        return spaces.findClassification(tracker.classificationId())
                .orElseThrow(() -> new BindingFailure(Reason.SharingModeMismatch, tracker.classificationId()));
    }

    /** 非共享派生：创建目标 Tracker 与 Source-backed 的 LightTrackerItem，不复制业务内容。 */
    @Transactional
    public DeriveResult derive(DeriveCommand command) {
        var workingSet = writable(command.workingSetId());
        return bindTrackerDerived(workingSet, sourceTracker(command.sourceTrackerId()), command);
    }

    @Transactional
    public DeriveResult bindTrackerDerived(String spaceId, String classificationId, DeriveCommand command) {
        var workingSet = writable(command.workingSetId());
        var source = sourceTracker(command.sourceTrackerId());
        if (!source.productSpaceId().equals(spaceId) || !source.classificationId().equals(classificationId)) {
            throw new BindingFailure(Reason.TrackerOutsideSlot, source.trackerId().value());
        }
        return bindTrackerDerived(workingSet, source, command);
    }

    private DeriveResult bindTrackerDerived(WorkingSet workingSet, Tracker source, DeriveCommand command) {
        workingSet.requireDirectUpstream(source);
        if (source.sharingMode() != SharingMode.UNSHARED) {
            throw new BindingFailure(Reason.SharingModeMismatch, source.classificationId());
        }
        Tracker tracker = Tracker.derived(TrackerId.of(String.valueOf(identifierGenerator.nextId())),
                source, workingSet.id());
        var binding = workingSet.bindTracker(tracker, classification(source), SharingMode.UNSHARED);
        var sourceItem = businessItems.findIdentity(command.sourceLocalId()).orElseThrow(
                () -> new BindingFailure(Reason.SourceItemNotFound, command.sourceLocalId().value()));
        if (!source.trackerId().equals(sourceItem.trackerId()) || !command.commonId().equals(sourceItem.commonId())) {
            throw new BindingFailure(Reason.SourceItemMismatch, command.sourceLocalId().value());
        }
        LightTrackerItem item = LightTrackerItem.derive(
                LocalId.of(String.valueOf(identifierGenerator.nextId())),
                sourceItem.commonId(),
                tracker.trackerId(),
                command.workingSetId(),
                command.sourceRevision(),
                command.sourceLocalId());
        trackers.insertTracker(tracker);
        workingSets.insertTrackerBinding(binding);
        items.save(item);
        return new DeriveResult(tracker, item);
    }

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
        sk.workingset.domain.Revision contentRevision = item.effectiveContentRevision();

        TrackerItemReadPort port = readPorts.apply(contentLocalId);
        if (!port.authorize(Operation.READ, caller)) {
            throw new UnsupportedOperationException("authorization is not enforced in S1");
        }
        TrackerItemContent content = port.readAt(contentRevision);
        return new ReadResult(content, item.materialized(), contentLocalId, contentRevision);
    }
}
