package sk.workingset.application.command;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sk.common.error.DomainException;
import sk.productspace.domain.ProductSpace;
import sk.productspace.domain.repository.ProductSpaceRepository;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.application.FailureReason;
import sk.workingset.application.query.provider.ClassificationCatalog;
import sk.workingset.application.query.provider.TrackerQueryProvider;
import sk.workingset.application.query.provider.WorkingSetQueryProvider;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.workingset.WorkingSet;
import sk.workingset.domain.workingset.WorkingSetId;
import sk.workingset.domain.workingset.WorkingSetRepository;
import sk.workingset.error.BindingResCode;
import sk.workingset.error.WorkingsetResultCode;

import java.util.List;

/**
 * Working Set 命令侧应用服务：谱系、纳入和绑定写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingSetCommandService {

    private final IdentifierGenerator identifierGenerator;
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final ProductSpaceRepository spaces;
    private final ClassificationCatalog classifications;
    private final WorkingSetQueryProvider queries;
    private final TrackerQueryProvider trackerQueries;
    private final ApplicationEventPublisher events;

    private static void requireUnshared(Tracker source) {
        if (source.sharingMode() != SharingMode.UNSHARED) {
            throw DomainException.withMessage(BindingResCode.SHARING_MODE_MISMATCH, source.classificationId());
        }
    }

    @Transactional
    public WorkingSet createChildWorkingSet(WorkingSetId parentId, WorkingSetId newId) {
        workingSets.requireReady(newId);
        return createChildForTask(parentId, newId, null, null, null);
    }

    @Transactional
    public WorkingSet createChildWorkingSet(WorkingSetId parentId, String name, String color) {
        return createChildForTask(parentId, WorkingSetId.of(identifierGenerator.nextId(null).longValue()), name, color, null);
    }

    // Called by the creation runner with the user-supplied display name.
    WorkingSet createChildForTask(WorkingSetId parentId, WorkingSetId newId, String name, String color, Long actor) {
        if (newId.equals(parentId)) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_CYCLE, newId.value());
        }
        writable(parentId);
        if (queries.findAncestors(parentId.numericValue()).contains(newId.numericValue())) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_CYCLE, newId.value());
        }
        WorkingSet child = WorkingSet.child(newId, parentId, name, color);
        try {
            workingSets.save(child);
        } catch (DomainException conflict) {
            if (conflict.getResCode() != BindingResCode.WORKING_SET_ALREADY_EXISTS) {
                throw conflict;
            }
            List<WorkingSetId> existing = queries.findAncestors(newId.numericValue()).stream().map(WorkingSetId::of).toList();
            boolean differentParent = !existing.isEmpty() && !parentId.equals(existing.getFirst());
            BindingResCode reason = differentParent ? BindingResCode.WORKING_SET_PARENT_IMMUTABLE : BindingResCode.WORKING_SET_ALREADY_EXISTS;
            throw DomainException.withMessage(reason, newId.value());
        }
        return child;
    }

    @Transactional
    public WorkingSet updatePresentation(WorkingSetId id, String name, String color) {
        if (name == null && color == null) {
            throw new DomainException(WorkingsetResultCode.BAD_REQUEST, "name or color is required");
        }
        WorkingSet target = writable(id);
        if (name != null) {
            target.rename(name);
        }
        if (color != null) {
            target.recolor(color);
        }
        workingSets.save(target);
        return target;
    }

    @Transactional
    public WorkingSet.SpaceBinding bindSpace(WorkingSetId id, String spaceId) {
        WorkingSet workingSet = writable(id);
        WorkingSet.SpaceBinding binding = bindSpace(workingSet, lockSpace(spaceId));
        workingSets.save(workingSet);
        events.publishEvent(new sk.workingset.domain.event.WorkingSetSpaceBoundEvent(id, spaceId));
        return binding;
    }

    private ProductSpace lockSpace(String spaceId) {
        return spaces.findById(spaceId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.PRODUCT_SPACE_NOT_FOUND, spaceId));
    }

    private WorkingSet.SpaceBinding bindSpace(WorkingSet workingSet, ProductSpace space) {
        return workingSet.bindSpace(space, WorkingSet.SpaceOrigin.RELATION);
    }

    @Transactional
    public void setTrackerShown(WorkingSetId workingSet, TrackerId tracker, boolean shown) {
        WorkingSet target = writable(workingSet);
        target.setTrackerShown(tracker, shown);
        workingSets.save(target);
    }

    @Transactional
    public WorkingSet.TrackerBinding bindTrackerShared(WorkingSetId id, TrackerId trackerId) {
        WorkingSet workingSet = writable(id);
        WorkingSet.TrackerBinding binding =
                bindTracker(workingSet, directParentTracker(workingSet, trackerId), SharingMode.SHARED);
        workingSets.save(workingSet);
        return binding;
    }

    private WorkingSet.TrackerBinding bindTracker(WorkingSet target, Tracker tracker, SharingMode mode) {
        return target.bindTracker(tracker, classification(tracker), mode);
    }

    // These methods run only under the creation task row lock and the item transaction.
    boolean bindSpaceForTask(WorkingSetId id, String spaceId, long actor) {
        WorkingSet target = lockTaskTarget(id);
        WorkingSet parent = writable(target.requireParentId());
        ProductSpace space = lockSpace(spaceId);
        if (!WorkingSet.DEFAULT_ID.equals(parent.id()) && !parent.hasSpaceBinding(spaceId)) {
            throw DomainException.withMessage(FailureReason.NOT_INCLUDED, spaceId);
        }
        if (target.hasSpaceBinding(spaceId)) {
            return false;
        }
        bindSpace(target, space);
        workingSets.save(target);
        events.publishEvent(new sk.workingset.domain.event.WorkingSetSpaceBoundEvent(id, spaceId));
        return true;
    }

    boolean copyUnsharedTrackerForTask(WorkingSetId id, TrackerId parentTrackerId, boolean visible, long actor) {
        WorkingSet target = lockTaskTarget(id);
        Tracker source = directParentTracker(target, parentTrackerId);
        requireUnshared(source);
        target.requireSpaceBinding(source.productSpaceId());
        if (trackers.findDerivedTracker(id, parentTrackerId).isPresent()) {
            return false;
        }
        Tracker copy = derivedTracker(target, source, visible, actor);
        trackers.save(copy);
        return true;
    }

    void revealExtensionTrackers(WorkingSetId target, List<TrackerId> parentTrackerIds) {
        trackers.revealExtensionTrackers(target, parentTrackerIds);
    }

    boolean bindUnsharedTrackerForTask(WorkingSetId id, TrackerId parentTrackerId) {
        WorkingSet target = lockTaskTarget(id);
        directParentTracker(target, parentTrackerId);
        Tracker copy = trackers.findDerivedTracker(id, parentTrackerId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.SOURCE_TRACKER_NOT_FOUND, parentTrackerId.value()));
        if (target.hasTrackerBinding(copy.trackerId())) {
            return false;
        }
        bindTracker(target, copy, SharingMode.UNSHARED);
        workingSets.save(target);
        return true;
    }

    boolean bindSharedTrackerForTask(WorkingSetId id, TrackerId parentTrackerId) {
        WorkingSet target = lockTaskTarget(id);
        Tracker source = directParentTracker(target, parentTrackerId);
        if (target.hasTrackerBinding(source.trackerId())) {
            return false;
        }
        bindTracker(target, source, SharingMode.SHARED);
        workingSets.save(target);
        return true;
    }

    String trackerSpaceForTask(TrackerId id) {
        return trackers.findById(id).map(Tracker::productSpaceId).orElse(null);
    }

    private WorkingSet lockTaskTarget(WorkingSetId id) {
        return workingSets.findByIdForCreation(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, id.value()));
    }

    private WorkingSet writable(WorkingSetId id) {
        return workingSets.findById(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, id.value()));
    }

    private Tracker directParentTracker(WorkingSet target, TrackerId id) {
        WorkingSetId parentId = target.requireParentId();
        // 非根父级锁定绑定范围，串行化解绑与新纳入。根原生 Tracker 由 Tracker 行锁保护。
        boolean boundInParent = !WorkingSet.DEFAULT_ID.equals(parentId) && writable(parentId).hasTrackerBinding(id);
        Tracker source = trackers.lockTracker(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.SOURCE_TRACKER_NOT_FOUND, id.value()));
        target.requireDirectParent(source, boundInParent);
        return source;
    }

    private Classification classification(Tracker tracker) {
        return classifications.findClassification(tracker.classificationId())
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.SHARING_MODE_MISMATCH, tracker.classificationId()));
    }

    private Tracker derivedTracker(WorkingSet target, Tracker source, boolean visible, long actor) {
        boolean isDefault = !trackerQueries.existsDefaultTracker(target.id(), source.productSpaceId(),
                source.classificationId());
        return Tracker.derived(TrackerId.of(String.valueOf(identifierGenerator.nextId(null).longValue())), source, target.id(),
                isDefault);
    }

}
