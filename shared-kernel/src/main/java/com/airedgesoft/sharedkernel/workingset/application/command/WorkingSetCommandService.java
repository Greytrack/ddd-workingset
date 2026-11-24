package com.airedgesoft.sharedkernel.workingset.application.command;

import com.airedgesoft.lib.id.Ids;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.businessframework.application.context.AuthorizationScope;
import com.airedgesoft.sharedkernel.productspace.domain.ProductSpace;
import com.airedgesoft.sharedkernel.productspace.domain.repository.ProductSpaceRepository;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.businessframework.application.query.ClassificationQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.TrackerQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.WorkingSetQueryProvider;
import com.airedgesoft.sharedkernel.workingset.domain.Classification;
import com.airedgesoft.sharedkernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSet;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation.Failure;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation.Request;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetCreation.Stage;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import com.airedgesoft.sharedkernel.workingset.error.WorkingsetResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Working Set 命令侧应用服务：谱系、纳入和绑定写入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkingSetCommandService {

        private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;
    private final ProductSpaceRepository spaces;
    private final WorkingSetQueryProvider queries;
    private final TrackerQueryProvider trackerQueries;
    private final ClassificationQueryProvider classifications;

    private static void requireUnshared(Tracker source) {
        if (source.sharingMode() != SharingMode.UNSHARED) {
            throw DomainException.withMessage(BindingResCode.SHARING_MODE_MISMATCH, source.classificationId());
        }
    }

    private static void requireTrackerCopies(String spaceId, List<TrackerToBind> candidates,
                                             List<Long> requiredClassifications) {
        // 空间成员关系由 TrackerBinding 投影，新增空间必须带上已派生的必带 Tracker。
        if (!requiredClassifications.stream().allMatch(classification -> candidates.stream()
                .map(TrackerToBind::tracker)
                .anyMatch(tracker -> tracker.sharingMode() == SharingMode.UNSHARED
                        && classification.equals(tracker.numericClassificationId())))) {
            throw DomainException.withMessage(BindingResCode.TRACKER_NOT_FOUND, spaceId);
        }
    }

    private static Failure bindingFailure(String spaceId, String trackerId, DomainException failure) {
        if (failure.getResCode() instanceof BindingResCode code) {
            return new Failure(Stage.BIND_PRODUCT_AND_TRACKER, spaceId, trackerId, null, code.reason(), System.currentTimeMillis());
        }
        FailureReason reason = FailureReason.of(failure);
        if (reason == FailureReason.PREPARING) {
            throw failure;
        }
        return new Failure(Stage.BIND_PRODUCT_AND_TRACKER, spaceId, trackerId, null, reason.name(), System.currentTimeMillis());
    }

    /**
     * 在任务上下文按指定 ID 创建子工作集；该 ID 可尚不存在。
     */
    @Transactional
    public WorkingSet createChildWorkingSet(Long parentId, Long newId) {
        // 新 ID 可以不存在，普通查询仍会拒绝创建任务正在占用的 ID。
        workingSets.findById(newId);
        return createChildForTask(parentId, newId, null, null, null);
    }

    /**
     * 创建子工作集并分配新雪花 ID。
     */
    @Transactional
    public WorkingSet createChildWorkingSet(Long parentId, String name, String color) {
        return createChildForTask(parentId, Ids.nextId(), name, color,
                AuthorizationScope.currentUserId());
    }

    // Called by the creation runner with the user-supplied display name.
    WorkingSet createChildForTask(Long parentId, Long newId, String name, String color, Long actor) {
        if (newId.equals(parentId)) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_CYCLE, Long.toString(newId));
        }
        workingSets.findByIdForChildCreation(parentId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(parentId)));
        if (queries.findAncestors(parentId).contains(newId)) {
            throw DomainException.withMessage(BindingResCode.WORKING_SET_CYCLE, Long.toString(newId));
        }
        Long owner = actor == null ? AuthorizationScope.currentUserId() : actor;
        WorkingSet child = WorkingSet.child(newId, parentId, name, color, owner, String.valueOf(owner));
        try {
            workingSets.save(child);
        } catch (DomainException conflict) {
            if (conflict.getResCode() != BindingResCode.WORKING_SET_ALREADY_EXISTS) {
                throw conflict;
            }
            List<Long> existing = queries.findAncestors(newId).stream().toList();
            boolean differentParent = !existing.isEmpty() && !parentId.equals(existing.getFirst());
            BindingResCode reason = differentParent ? BindingResCode.WORKING_SET_PARENT_IMMUTABLE : BindingResCode.WORKING_SET_ALREADY_EXISTS;
            throw DomainException.withMessage(reason, Long.toString(newId));
        }
        return child;
    }

    /**
     * 编辑工作集显示名称与主题色；缺失字段保持不变。
     */
    @Transactional
    public WorkingSet updatePresentation(Long id, String name, String color) {
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
        target.audit(Long.toString(AuthorizationScope.currentUserId()));
        workingSets.save(target);
        return target;
    }

    /**
     * 在保存绑定前确认空间有效，并与产品空间删除协调。
     */
    private ProductSpace lockSpace(String spaceId) {
        return spaces.findByIdForBinding(spaceId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.PRODUCT_SPACE_NOT_FOUND, spaceId));
    }


    /**
     * 切换 Tracker 在工作集的显示状态。
     */
    @Transactional
    public void setTrackerShown(Long workingSet, long spaceId, Long tracker, boolean shown) {
        WorkingSet target = writable(workingSet);
        target.setTrackerShown(spaceId, tracker, shown);
        target.audit(Long.toString(AuthorizationScope.currentUserId()));
        workingSets.save(target);
    }

    // These methods run only under the creation task row lock and the item transaction.
    BindingResult bindProductAndTrackersForTask(Long id, Request request, Set<String> failedCopies,
                                                List<Long> requiredClassifications) {
        WorkingSet target = lockTaskTarget(id);
        WorkingSet parent = writable(target.requireParentId());
        Map<String, List<TrackerToBind>> bySpace = new LinkedHashMap<>();
        request.productSpaceIds().forEach(spaceId -> bySpace.put(spaceId, new ArrayList<>()));
        List<Failure> failures = new ArrayList<>();

        for (String sourceId : request.unsharedParentTrackerIds()) {
            if (!failedCopies.contains(sourceId)) {
                collectBinding(target, sourceId, SharingMode.UNSHARED, bySpace, failures);
            }
        }
        for (String sourceId : request.sharedTrackerIds()) {
            collectBinding(target, sourceId, SharingMode.SHARED, bySpace, failures);
        }

        boolean changed = false;
        for (Map.Entry<String, List<TrackerToBind>> selection : bySpace.entrySet()) {
            String spaceId = selection.getKey();
            List<TrackerToBind> candidates = selection.getValue();
            try {
                ProductSpace space = lockSpace(spaceId);
                if (!WorkingSet.DEFAULT_ID.equals(parent.id()) && !parent.hasSpaceBinding(spaceId)) {
                    throw DomainException.withMessage(FailureReason.NOT_INCLUDED, spaceId);
                }
                if (!target.hasSpaceBinding(spaceId)) {
                    requireTrackerCopies(spaceId, candidates, requiredClassifications);
                    target.bindSpace(space, WorkingSet.SpaceOrigin.RELATION);
                }
                for (TrackerToBind candidate : candidates) {
                    Tracker tracker = candidate.tracker();
                    if (!target.hasTrackerBinding(tracker.trackerId())) {
                        target.bindTracker(tracker, candidate.classification(), tracker.sharingMode());
                        changed = true;
                    }
                }
            } catch (DomainException failure) {
                failures.add(bindingFailure(spaceId, null, failure));
            }
        }

        if (changed) {
            target.audit(Long.toString(AuthorizationScope.currentUserId()));
            workingSets.save(target);
        }
        return new BindingResult(changed, failures);
    }

    private void collectBinding(WorkingSet target, String sourceId, SharingMode mode,
                                Map<String, List<TrackerToBind>> bySpace, List<Failure> failures) {
        Long id = Long.valueOf(sourceId);
        String spaceId = null;
        try {
            Tracker source = directParentTracker(target, id);
            spaceId = source.productSpaceId().toString();
            List<TrackerToBind> scope = bySpace.get(spaceId);
            if (scope == null) {
                throw DomainException.withMessage(BindingResCode.MISSING_SPACE_BINDING, spaceId);
            }
            Tracker tracker = mode == SharingMode.SHARED ? source : trackers.findDerivedTracker(target.id(), id)
                    .orElseThrow(() -> DomainException.withMessage(BindingResCode.SOURCE_TRACKER_NOT_FOUND, sourceId));
            Classification classification = classifications.requireClassification(tracker.classificationId());
            if (tracker.sharingMode() != mode || !classification.allows(mode)) {
                throw DomainException.withMessage(BindingResCode.SHARING_MODE_MISMATCH, classification.id());
            }
            scope.add(new TrackerToBind(tracker, classification));
        } catch (DomainException failure) {
            failures.add(bindingFailure(spaceId == null ? trackerSpaceForTask(id) : spaceId, sourceId, failure));
        }
    }

    boolean deriveUnsharedTrackerForTask(Long id, Long parentTrackerId,
                                         List<String> selectedSpaces, long actor) {
        WorkingSet target = lockTaskTarget(id);
        Tracker source = directParentTracker(target, parentTrackerId);
        requireUnshared(source);
        if (!selectedSpaces.contains(source.productSpaceId().toString())) {
            throw DomainException.withMessage(BindingResCode.MISSING_SPACE_BINDING, source.productSpaceId().toString());
        }
        if (trackers.findDerivedTracker(id, parentTrackerId).isPresent()) {
            return false;
        }
        Tracker copy = derivedTracker(target, source, actor);
        trackers.save(copy);
        return true;
    }

    String trackerSpaceForTask(Long id) {
        return trackers.findById(id).map(tracker -> tracker.productSpaceId().toString()).orElse(null);
    }

    private WorkingSet lockTaskTarget(Long id) {
        return workingSets.findByIdForTaskWrite(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(id)));
    }

    private WorkingSet writable(Long id) {
        return workingSets.findById(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(id)));
    }

    private Tracker directParentTracker(WorkingSet target, Long id) {
        Long parentId = target.requireParentId();
        boolean boundInParent = !WorkingSet.DEFAULT_ID.equals(parentId) && writable(parentId).hasTrackerBinding(id);
        Tracker source = trackers.findById(id)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.SOURCE_TRACKER_NOT_FOUND, Long.toString(id)));
        target.requireDirectParent(source, boundInParent);
        return source;
    }

    private Tracker derivedTracker(WorkingSet target, Tracker source, long actor) {
        boolean isDefault = !trackerQueries.existsDefaultTracker(target.id(), source.productSpaceId().toString(),
                source.classificationId());
        return Tracker.derived(Long.valueOf(String.valueOf(Ids.nextId())), source, target.id(),
                isDefault, actor);
    }

    record BindingResult(boolean changed, List<Failure> failures) {
    }

    private record TrackerToBind(Tracker tracker, Classification classification) {
    }

}
