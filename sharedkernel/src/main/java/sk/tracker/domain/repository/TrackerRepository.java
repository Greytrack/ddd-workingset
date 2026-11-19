package sk.tracker.domain.repository;

import org.jmolecules.ddd.annotation.Repository;
import sk.tracker.domain.Tracker;
import sk.tracker.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

import java.util.List;
import java.util.Optional;

/**
 * Tracker 聚合存取与删除前置查询：组织子级、派生子级、空间内 Tracker。
 */
@Repository
public interface TrackerRepository {
    Optional<Tracker> findTracker(TrackerId id);

    Optional<Tracker> lockTracker(TrackerId id);

    Optional<Tracker> findDerivedTracker(WorkingSetId target, TrackerId source);

    Optional<Tracker> findRootFolder(WorkingSetId owner, String spaceId);

    /**
     * 列出归属于或绑定到工作集的可见 Tracker。
     */
    List<Tracker> findTrackers(WorkingSetId workingSetId);

    void insertTracker(Tracker tracker);

    void updateName(Tracker tracker);

    /**
     * 扩展任务创建的新 Tracker 在任务终态前不可见。
     */
    void insertTrackerForTask(Tracker tracker, boolean visible, long actor);

    boolean isVisible(TrackerId trackerId);

    /**
     * 终态后只揭示已成功建立绑定的新 Tracker。
     */
    void revealExtensionTrackers(WorkingSetId target, List<TrackerId> parentTrackerIds, long actor);

    boolean hasOrgChildren(TrackerId id);

    boolean hasDerivedChildren(TrackerId id);

    boolean hasTrackersInSpace(String spaceId);

    boolean existsDefaultTracker(WorkingSetId workingSetId, String spaceId, String classificationId);

    void deleteTracker(TrackerId id);
}
