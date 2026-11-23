package com.airedgesoft.alm.requirement.application.query;

import com.airedgesoft.alm.requirement.application.query.model.RequirementDetail;
import com.airedgesoft.alm.requirement.application.query.provider.RequirementQueryProvider;
import com.airedgesoft.alm.requirement.domain.Requirement;
import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import com.airedgesoft.sharedkernel.tracker.domain.repository.TrackerRepository;
import com.airedgesoft.sharedkernel.workingset.application.FailureReason;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.LightTrackerItemQueryProvider;
import com.airedgesoft.sharedkernel.workingset.application.query.provider.LightTrackerItemSummary;
import com.airedgesoft.sharedkernel.workingset.domain.Classification.SharingMode;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharedkernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import com.airedgesoft.sharedkernel.workingset.domain.workingset.WorkingSetRepository;
import com.airedgesoft.sharedkernel.workingset.error.BindingResCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Requirement 查询侧应用服务：读模型优先，继承目标回退。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RequirementQueryService {
    private final RequirementQueryProvider query;
    private final LightTrackerItemQueryProvider itemQueries;
    private final LightTrackerItemRepository items;
    private final WorkingSetRepository workingSets;
    private final TrackerRepository trackers;

    private static DomainException notFound(Long localId) {
        return DomainException.withMessage(FailureReason.NOT_FOUND, "requirement not found: " + Long.toString(localId));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public RequirementDetail find(Long localId) {
        Optional<RequirementDetail> persisted = query.find(localId);
        if (persisted.isEmpty()) {
            return findInherited(localId);
        }
        RequirementDetail detail = persisted.get();
        if (detail.deleted()) {
            throw DomainException.withMessage(FailureReason.DELETED, Long.toString(localId));
        }
        return detail;
    }

    /**
     * 共享 Tracker 的 Item 属于其权威工作集，但通过绑定对所有子工作集可见；
     * 非共享 Tracker 的 Item 属于单一工作集。工作集参数用于可见性校验，列表按 Tracker 读取。
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public List<RequirementDetail> findByTracker(Long workingSetId, Long trackerId) {
        Tracker tracker = trackers.findById(trackerId)
                .orElseThrow(() -> DomainException.withMessage(FailureReason.NOT_FOUND, Long.toString(trackerId)));
        if (!visibleIn(tracker, workingSetId)) {
            return List.of();
        }
        List<RequirementDetail> localRows = query.findByTracker(trackerId);
        Set<Long> localIds = localRows.stream().map(RequirementDetail::localId).collect(Collectors.toSet());
        List<LightTrackerItemSummary> inherited = itemQueries.findInTracker(trackerId).stream()
                .filter(item -> !item.materialized() && !localIds.contains(item.localId()))
                .toList();
        List<Long> sourceIds = inherited.stream().map(LightTrackerItemSummary::sourceLocalId).distinct().toList();
        Map<Long, RequirementDetail> sources = query.findByIds(sourceIds).stream()
                .collect(Collectors.toMap(RequirementDetail::localId, Function.identity()));
        Stream<RequirementDetail> inheritedRows = inherited.stream().flatMap(item ->
                Optional.ofNullable(sources.get(item.sourceLocalId()))
                        .filter(source -> !source.deleted() && item.sourceRevision().equals(source.revision()))
                        .map(source -> inheritedDetail(item, source)).stream());
        return Stream.concat(localRows.stream().filter(row -> !row.deleted()), inheritedRows)
                .sorted(Comparator.comparing(RequirementDetail::localId))
                .toList();
    }

    private boolean visibleIn(Tracker tracker, Long workingSetId) {
        if (tracker.workingSetId().equals(workingSetId)) {
            return true;
        }
        if (tracker.sharingMode() != SharingMode.SHARED) {
            return false;
        }
        return workingSets.findById(workingSetId)
                .map(workingSet -> workingSet.hasTrackerBinding(tracker.trackerId()))
                .orElse(false);
    }

    private RequirementDetail findInherited(Long localId) {
        LightTrackerItem target = items.findById(localId)
                .filter(item -> !item.materialized())
                .orElseThrow(() -> notFound(localId));
        Long workingSetId = target.workingSetId();
        workingSets.findById(workingSetId)
                .orElseThrow(() -> DomainException.withMessage(BindingResCode.WORKING_SET_NOT_EXISTS, Long.toString(workingSetId)));
        RequirementDetail source = query.find(target.sourceLocalId())
                .orElseThrow(() -> notFound(target.sourceLocalId()));
        if (source.deleted()) {
            throw DomainException.withMessage(FailureReason.DELETED, Long.toString(source.localId()));
        }
        if (!target.sourceRevision().equals(source.revision())) {
            throw DomainException.withMessage(FailureReason.HISTORY_UNAVAILABLE, Long.toString(target.sourceLocalId()));
        }
        return inheritedDetail(LightTrackerItemSummary.from(target), source);
    }

    private static RequirementDetail inheritedDetail(LightTrackerItemSummary target, RequirementDetail source) {
        Requirement requirement = Requirement.inherited(target.localId(), target.commonId(), target.trackerId(),
                source.title(), source.description(), target.revision(), source.version());
        return new RequirementDetail(
                requirement.localId(), requirement.commonId(),
                requirement.trackerId(), requirement.number(), requirement.title(),
                requirement.description(), requirement.revision(), requirement.version(),
                requirement.lockVersion(), requirement.deleted(), requirement.hasMaterialized(), false, source.flags());
    }
}
