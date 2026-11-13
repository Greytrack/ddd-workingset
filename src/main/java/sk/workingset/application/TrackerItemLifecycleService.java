package sk.workingset.application;

import org.springframework.transaction.annotation.Transactional;

import sk.workingset.application.port.LightTrackerItemRepository;
import sk.workingset.application.port.MaterializationRejectedException;
import sk.workingset.application.port.TargetIdentity;
import sk.workingset.application.port.TrackerItemLifecyclePort;
import sk.workingset.application.port.TrackerRepository;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.TrackerId;

/**
 * SK 拥有的 LTI 生命周期协作实现（D4 方案 A）。
 *
 * <p>Tracker 与 LightTrackerItem 均属 SK；业务 BC 只通过 {@link TrackerItemLifecyclePort}
 * 请求协作，不再直接读写 SK 仓储。永久删除后的 LTI 清理由业务对象删除事件驱动。
 */
public class TrackerItemLifecycleService implements TrackerItemLifecyclePort {

    private final TrackerRepository trackers;
    private final LightTrackerItemRepository items;

    public TrackerItemLifecycleService(TrackerRepository trackers, LightTrackerItemRepository items) {
        this.trackers = trackers;
        this.items = items;
    }

    @Override
    @Transactional
    public TargetIdentity lockMaterializationTarget(TrackerId trackerId, LocalId targetLocalId) {
        trackers.lockTracker(trackerId);
        return items.findById(targetLocalId)
                .map(TargetIdentity::of)
                .orElseThrow(() -> new MaterializationRejectedException("Target variant is missing"));
    }

    @Override
    @Transactional
    public void assertPurgeable(TrackerId trackerId, LocalId localId) {
        trackers.lockTracker(trackerId);
        if (items.hasSourceBackedDependents(localId)
                || items.findById(localId).filter(item -> !item.materialized()).isPresent()) {
            throw new ApplicationFailure(FailureReason.DEPENDENCY_UNAVAILABLE,
                    "Requirement content is still needed by COW");
        }
    }

    @Override
    @Transactional
    public void removePurged(LocalId localId) {
        items.remove(localId);
    }
}
