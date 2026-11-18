package sk.workingset.application.command;

import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;

import java.util.Optional;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sk.workingset.application.common.InheritedEditTarget;
import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.workingset.error.WorkingsetResultCode;
import sk.workingset.domain.event.MergeTargetEvent;
import sk.workingset.domain.event.TrackerItemCreatedEvent;
import sk.workingset.application.common.TargetIdentity;
import sk.workingset.application.port.BusinessTrackerItems;
import sk.workingset.application.port.TrackerItemLifecyclePort;
import sk.workingset.domain.repository.TrackerRepository;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
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
    private final BusinessTrackerItems businessItems;

    public TrackerItemLifecycleService(TrackerRepository trackers, LightTrackerItemRepository items,
                                       BusinessTrackerItems businessItems) {
        this.trackers = trackers;
        this.items = items;
        this.businessItems = businessItems;
    }

    @Override
    public Optional<InheritedEditTarget> findInheritedTarget(LocalId localId) {
        return items.findById(localId).filter(item -> !item.materialized())
                .map(item -> new InheritedEditTarget(TargetIdentity.of(item), item.workingSetId(),
                        item.sourceLocalId(), item.sourceRevision()));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<InheritedEditTarget> lockInheritedEditTarget(LocalId localId) {
        var candidate = items.findById(localId).filter(item -> !item.materialized());
        if (candidate.isEmpty()) {
            return Optional.empty();
        }
        trackers.lockTracker(candidate.orElseThrow().trackerId());
        return findInheritedTarget(localId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeInheritedEdit(LocalId localId, Revision revision) {
        switchToLocalContent(localId, revision);
    }

    @Override
    @Transactional
    public TargetIdentity lockMaterializationTarget(TrackerId trackerId, LocalId targetLocalId) {
        trackers.lockTracker(trackerId);
        return items.findById(targetLocalId)
                .map(TargetIdentity::of)
                .orElseThrow(() -> DomainException.withMessage(WorkingsetResultCode.MATERIALIZATION_REJECTED, "Target variant is missing"));
    }

    @Transactional
    public void removePurged(LocalId localId) {
        items.remove(localId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registerCreated(TrackerItemCreatedEvent event) {
        // Match delete2's lock order so a late creation event cannot restore a purged index.
        trackers.lockTracker(event.trackerId());
        if (!businessItems.lockContentSource(event.localId()) || items.findById(event.localId()).isPresent()) {
            return;
        }
        items.save(LightTrackerItem.local(event.localId(), event.commonId(), event.trackerId(),
                event.workingSetId(), event.revision()));
    }

    @Override
    @Transactional
    public void applyMergeTarget(MergeTargetEvent target) {
        if (target.inherited()) {
            switchToLocalContent(target.localId(), target.revision());
            return;
        }
        if (target.create()) {
            items.save(LightTrackerItem.local(target.localId(), target.commonId(), target.trackerId(),
                    target.workingSetId(), target.revision()));
        }
    }

    private void switchToLocalContent(LocalId localId, Revision revision) {
        var item = items.findById(localId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND,
                        "target variant disappeared: " + localId.value()));
        if (!item.materialized()) {
            item.switchToLocalBacked(revision);
            items.save(item);
        }
    }
}
