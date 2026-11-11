package sk.workingset.application;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

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
 */
public class WorkingSetApplicationService {

    private final LightTrackerItemRepository items;
    private final Function<LocalId, TrackerItemReadPort> readPorts;

    public WorkingSetApplicationService(LightTrackerItemRepository items,
                                        Function<LocalId, TrackerItemReadPort> readPorts) {
        this.items = Objects.requireNonNull(items, "items");
        this.readPorts = Objects.requireNonNull(readPorts, "readPorts");
    }

    /** 非共享派生：创建目标 Tracker 与 Source-backed 的 LightTrackerItem，不复制业务内容。 */
    public DeriveResult derive(DeriveCommand command) {
        Objects.requireNonNull(command, "command");
        Tracker tracker = Tracker.derived(
                TrackerId.of("T-" + UUID.randomUUID()),
                command.sourceTrackerId(),
                command.workingSetId());
        LightTrackerItem item = LightTrackerItem.derive(
                LocalId.newId(),
                command.commonId(),
                tracker.trackerId(),
                command.workingSetId(),
                command.sourceRevision(),
                command.sourceLocalId());
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
