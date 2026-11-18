package sk.workingset.application.command;

import sk.workingset.application.ApplicationFailure;
import sk.workingset.application.FailureReason;

import java.util.Optional;

import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import sk.workingset.domain.repository.LightTrackerItemRepository;
import sk.common.error.DomainException;
import sk.workingset.error.WorkingsetResultCode;
import sk.workingset.domain.event.MergeTarget;
import sk.workingset.application.common.TargetIdentity;
import sk.workingset.application.port.TrackerItemLifecyclePort;
import sk.workingset.domain.repository.TrackerRepository;
import sk.workingset.domain.CommonId;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.LocalId;
import sk.workingset.domain.Revision;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

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

    @Override
    @Transactional
    public void indexLocal(TrackerId trackerId, LocalId localId, CommonId commonId,
                           WorkingSetId workingSetId, Revision revision) {
        if (items.findById(localId).isPresent()) {
            return;
        }
        items.save(LightTrackerItem.local(localId, commonId, trackerId, workingSetId, revision));
    }

    @Override
    @Transactional
    public void applyMergeTarget(MergeTarget target) {
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
                .orElseThrow(() -> new ApplicationFailure(FailureReason.NOT_FOUND,
                        "target variant disappeared: " + localId.value()));
        if (!item.materialized()) {
            item.switchToLocalBacked(revision);
            items.save(item);
        }
    }
}
