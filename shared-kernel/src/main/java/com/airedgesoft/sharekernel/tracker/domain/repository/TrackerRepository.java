package com.airedgesoft.sharekernel.tracker.domain.repository;

import com.airedgesoft.sharekernel.tracker.domain.Tracker;
import org.jmolecules.ddd.annotation.Repository;

import java.util.Optional;

/**
 * Tracker 聚合存取与删除前置查询：组织子级、派生子级、空间内 Tracker。
 */
@Repository
public interface TrackerRepository {

    Optional<Tracker> findById(Long id);

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
