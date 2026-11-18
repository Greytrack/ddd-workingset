package sk.workingset.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.springframework.transaction.annotation.Transactional;

import sk.common.error.DomainException;
import sk.workingset.application.FailureReason;
import sk.workingset.domain.repository.ProductSpaceRepository;
import sk.workingset.domain.repository.TrackerRepository;
import sk.workingset.domain.repository.WorkingSetRepository;
import sk.workingset.domain.BindingFailure;
import sk.workingset.domain.BindingFailure.Reason;
import sk.workingset.domain.Classification;
import sk.workingset.domain.Classification.SharingMode;
import sk.workingset.domain.ProductSpace;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSet;
import sk.workingset.domain.WorkingSetId;

/** Working Set 命令侧应用服务：谱系、纳入和绑定写入。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingSetCommandService {

    private final IdentifierGenerator identifierGenerator;
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final ProductSpaceRepository spaces;


    @Transactional
    public WorkingSet createChildWorkingSet(WorkingSetId parentId, WorkingSetId newId) {
        workingSets.requireReady(newId);
        return createChildForTask(parentId, newId, null);
    }

    @Transactional
    public WorkingSet createChildWorkingSet(WorkingSetId parentId, String name) {
        return createChildForTask(parentId, WorkingSetId.of(identifierGenerator.nextId(null).longValue()), name);
    }

    // Called by the creation runner with the user-supplied display name.
    WorkingSet createChildForTask(WorkingSetId parentId, WorkingSetId newId, String name) {
        if (newId.equals(parentId)) {
            throw new BindingFailure(Reason.WorkingSetCycle, newId.value());
        }
        writable(parentId);
        if (workingSets.findAncestors(parentId).contains(newId)) {
            throw new BindingFailure(Reason.WorkingSetCycle, newId.value());
        }
        WorkingSet child = WorkingSet.child(newId, parentId, name);
        if (!workingSets.insertWorkingSet(child)) {
            List<WorkingSetId> existing = workingSets.findAncestors(newId);
            boolean differentParent = !existing.isEmpty() && !parentId.equals(existing.getFirst());
            Reason reason = differentParent ? Reason.WorkingSetParentImmutable : Reason.WorkingSetAlreadyExists;
            throw new BindingFailure(reason, newId.value());
        }
        return child;
    }

    @Transactional
    public Tracker createTracker(WorkingSetId id, String spaceId, String name, String classificationId,
                                 SharingMode sharingMode) {
        if (!WorkingSet.DEFAULT_ID.equals(id)) {
            throw new BindingFailure(Reason.WorkingSetNotWritable, "只允许在默认工作集新建跟踪分类");
        }
        WorkingSet workingSet = writable(id);
        ProductSpace space = lockSpace(spaceId);
        workingSet.requireSpaceBinding(spaceId);
        Classification classification = spaces.findClassification(classificationId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, "分类不存在：" + classificationId));
        Tracker tracker = Tracker.original(TrackerId.of(String.valueOf(identifierGenerator.nextId(null).longValue())),
                name, space, classification, sharingMode);
        WorkingSet.TrackerBinding binding = workingSet.bindTracker(tracker, classification, sharingMode);
        trackers.insertTracker(tracker);
        workingSets.insertTrackerBinding(binding);
        return tracker;
    }

    /**
     * 创建产品空间，并将空间和各分类的默认 Tracker 同步纳入默认工作集。
     *
     * <p>产品空间只能归属默认工作集：调用方传入的工作集入口仅用于路由，不参与目标选择，
     * 本方法一律固定使用 {@link WorkingSet#DEFAULT_ID}。入口层不限制调用来源，这是有意设计。
     */
    @Transactional
    public ProductSpace createProductSpace(String name) {
        ProductSpace space = new ProductSpace(identifierGenerator.nextId(null).longValue(), name);
        WorkingSet defaultWorkingSet = writable(WorkingSet.DEFAULT_ID);
        spaces.insertProductSpace(space);
        workingSets.insertSpaceBinding(defaultWorkingSet.bindSpace(space, WorkingSet.SpaceOrigin.INITIAL_PROJECTION));
        for (Classification classification : spaces.findClassifications()) {
            Tracker tracker = Tracker.original(TrackerId.of(String.valueOf(identifierGenerator.nextId(null).longValue())),
                    space, classification, null, true);
            WorkingSet.TrackerBinding binding = defaultWorkingSet.bindTracker(tracker, classification, classification.sharingMode());
            trackers.insertTracker(tracker);
            workingSets.insertTrackerBinding(binding);
        }
        return space;
    }

    @Transactional
    public WorkingSet.SpaceBinding bindSpace(WorkingSetId id, String spaceId) {
        return bindSpace(writable(id), lockSpace(spaceId));
    }

    private ProductSpace lockSpace(String spaceId) {
        return spaces.lockProductSpace(spaceId)
                .orElseThrow(() -> new BindingFailure(Reason.ProductSpaceNotFound, spaceId));
    }

    private WorkingSet.SpaceBinding bindSpace(WorkingSet workingSet, ProductSpace space) {
        WorkingSet.SpaceBinding binding = workingSet.bindSpace(space);
        workingSets.insertSpaceBinding(binding);
        return binding;
    }

    @Transactional
    public WorkingSet.TrackerBinding bindTrackerShared(WorkingSetId id, TrackerId trackerId) {
        WorkingSet workingSet = writable(id);
        return bindTracker(workingSet, directParentTracker(workingSet, trackerId), SharingMode.SHARED);
    }

    private WorkingSet.TrackerBinding bindTracker(WorkingSet target, Tracker tracker, SharingMode mode) {
        WorkingSet.TrackerBinding binding = target.bindTracker(tracker, classification(tracker), mode);
        workingSets.insertTrackerBinding(binding);
        return binding;
    }

    // These methods run only under the creation task row lock and the item transaction.
    boolean bindSpaceForTask(WorkingSetId id, String spaceId) {
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
        return true;
    }

    boolean copyUnsharedTrackerForTask(WorkingSetId id, TrackerId parentTrackerId, boolean visible) {
        WorkingSet target = lockTaskTarget(id);
        Tracker source = directParentTracker(target, parentTrackerId);
        requireUnshared(source);
        target.requireSpaceBinding(source.productSpaceId());
        if (trackers.findDerivedTracker(id, parentTrackerId).isPresent()) {
            return false;
        }
        Tracker copy = derivedTracker(target, source);
        if (visible) {
            trackers.insertTracker(copy);
        } else {
            trackers.insertInvisibleTracker(copy);
        }
        return true;
    }

    void revealExtensionTrackers(WorkingSetId target, List<TrackerId> parentTrackerIds) {
        trackers.revealExtensionTrackers(target, parentTrackerIds);
    }

    boolean bindUnsharedTrackerForTask(WorkingSetId id, TrackerId parentTrackerId) {
        WorkingSet target = lockTaskTarget(id);
        directParentTracker(target, parentTrackerId);
        Tracker copy = trackers.findDerivedTracker(id, parentTrackerId)
                .orElseThrow(() -> new BindingFailure(Reason.SourceTrackerNotFound, parentTrackerId.value()));
        if (target.hasTrackerBinding(copy.trackerId())) {
            return false;
        }
        bindTracker(target, copy, SharingMode.UNSHARED);
        return true;
    }

    boolean bindSharedTrackerForTask(WorkingSetId id, TrackerId parentTrackerId) {
        WorkingSet target = lockTaskTarget(id);
        Tracker source = directParentTracker(target, parentTrackerId);
        if (target.hasTrackerBinding(source.trackerId())) {
            return false;
        }
        bindTracker(target, source, SharingMode.SHARED);
        return true;
    }

    String trackerSpaceForTask(TrackerId id) {
        return trackers.findTracker(id).map(Tracker::productSpaceId).orElse(null);
    }

    private WorkingSet lockTaskTarget(WorkingSetId id) {
        return workingSets.lockWorkingSetForCreation(id)
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, id.value()));
    }

    private WorkingSet writable(WorkingSetId id) {
        return workingSets.lockWorkingSet(id)
                .orElseThrow(() -> new BindingFailure(Reason.WorkingSetNotFound, id.value()));
    }

    private Tracker directParentTracker(WorkingSet target, TrackerId id) {
        WorkingSetId parentId = target.requireParentId();
        // 非根父级锁定绑定范围，串行化解绑与新纳入。根原生 Tracker 由 Tracker 行锁保护。
        boolean boundInParent = !WorkingSet.DEFAULT_ID.equals(parentId) && writable(parentId).hasTrackerBinding(id);
        Tracker source = trackers.lockTracker(id)
                .orElseThrow(() -> new BindingFailure(Reason.SourceTrackerNotFound, id.value()));
        target.requireDirectParent(source, boundInParent);
        return source;
    }

    private Classification classification(Tracker tracker) {
        return spaces.findClassification(tracker.classificationId())
                .orElseThrow(() -> new BindingFailure(Reason.SharingModeMismatch, tracker.classificationId()));
    }

    private static void requireUnshared(Tracker source) {
        if (source.sharingMode() != SharingMode.UNSHARED) {
            throw new BindingFailure(Reason.SharingModeMismatch, source.classificationId());
        }
    }

    private Tracker derivedTracker(WorkingSet target, Tracker source) {
        boolean isDefault = !spaces.existsDefaultTracker(target.id(), source.productSpaceId(),
                source.classificationId());
        return Tracker.derived(TrackerId.of(String.valueOf(identifierGenerator.nextId(null).longValue())), source, target.id(),
                isDefault);
    }

}
