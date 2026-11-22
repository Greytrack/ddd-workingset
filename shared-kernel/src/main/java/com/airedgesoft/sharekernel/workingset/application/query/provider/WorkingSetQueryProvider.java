package com.airedgesoft.sharekernel.workingset.application.query.provider;

import com.airedgesoft.sharekernel.workingset.application.query.model.ClassificationSummary;
import com.airedgesoft.sharekernel.workingset.application.query.model.TrackerSummary;
import com.airedgesoft.sharekernel.workingset.application.query.model.WorkingSetBindings;
import com.airedgesoft.sharekernel.workingset.application.query.model.WorkingSetSummary;

import java.util.List;
import java.util.Optional;

/**
 * Working Set 查询侧端口；实现必须只读。
 */
public interface WorkingSetQueryProvider {

    Optional<WorkingSetSummary> find(long workingSetId);

    boolean isPreparing(long workingSetId);

    List<Long> findChildren(long workingSetId);

    List<Long> findAncestors(long workingSetId);

    List<TrackerSummary> findTrackers(long workingSetId);

    List<ClassificationSummary> findClassifications();

    WorkingSetBindings findBindings(long workingSetId);

    boolean hasWorkingSetBinding(String spaceId);

    boolean hasBindings(long workingSetId);

    List<Long> findWorkingSetIdsBoundToTracker(long trackerId);
}
