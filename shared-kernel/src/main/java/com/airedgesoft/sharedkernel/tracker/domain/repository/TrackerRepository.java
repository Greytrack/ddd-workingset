package com.airedgesoft.sharedkernel.tracker.domain.repository;

import com.airedgesoft.sharedkernel.tracker.domain.Tracker;
import org.jmolecules.ddd.annotation.Repository;

import java.util.Optional;

/**
 * Tracker 聚合存取与删除前置查询：组织子级、派生子级、空间内 Tracker。
 */
@Repository
public interface TrackerRepository {

    Optional<Tracker> findById(Long id);

    /**
     * 指定 Tracker ID 时直接按 ID 查询。未指定时按工作集、产品空间和子分类查询默认 Tracker。
     */
    Optional<Tracker> findTracker(Long workingsetId, Long workSpace, Long subClassId, Long trackerId);

    Optional<Tracker> lockTracker(Long id);

    Optional<Tracker> findDerivedTracker(Long target, Long source);

    Optional<Tracker> findDefaultTracker(Long owner, String spaceId, long classificationId);

    /**
     * 保存聚合及其兼容数据。
     */
    void save(Tracker tracker);

    /**
     * 物理删除聚合；软删除通过 {@link #save(Tracker)} 持久化聚合的 deleted 状态。
     */
    void delete(Tracker tracker);

}
