package sk.tracker.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.workingset.WorkingSetId;

import java.util.Optional;

/**
 * Tracker 聚合存取与删除前置查询：组织子级、派生子级、空间内 Tracker。
 */
@Repository
public interface TrackerRepository {
    Optional<Tracker> findById(TrackerId id);

    Optional<Tracker> lockTracker(TrackerId id);

    Optional<Tracker> findDerivedTracker(WorkingSetId target, TrackerId source);

    Optional<Tracker> findDefaultTracker(WorkingSetId owner, String spaceId, long classificationId);

    /**
     * 保存聚合及其兼容数据。
     */
    void save(Tracker tracker);

    void deleteTracker(TrackerId id);
}
