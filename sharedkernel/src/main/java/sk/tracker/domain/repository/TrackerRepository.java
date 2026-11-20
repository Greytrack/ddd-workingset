package sk.tracker.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.workingset.WorkingSetId;

import java.util.List;
import java.util.Optional;

/**
 * Tracker 聚合存取与删除前置查询：组织子级、派生子级、空间内 Tracker。
 */
@Repository
public interface TrackerRepository {
    Optional<Tracker> findById(TrackerId id);

    Optional<Tracker> lockTracker(TrackerId id);

    Optional<Tracker> findDerivedTracker(WorkingSetId target, TrackerId source);

    Optional<Tracker> findRootFolder(WorkingSetId owner, String spaceId);

    default void save(Tracker tracker) {
        save(tracker, true, null);
    }

    /**
     * 保存聚合及其兼容数据；初始可见性只在新建时使用。
     */
    void save(Tracker tracker, boolean initiallyVisible, Long actor);

    /**
     * 终态后只揭示已成功建立绑定的新 Tracker。
     */
    void revealExtensionTrackers(WorkingSetId target, List<TrackerId> parentTrackerIds, long actor);

    void deleteTracker(TrackerId id);
}
