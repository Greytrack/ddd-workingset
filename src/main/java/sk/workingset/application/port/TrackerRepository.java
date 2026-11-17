package sk.workingset.application.port;

import java.util.List;
import java.util.Optional;
import sk.workingset.domain.Tracker;
import sk.workingset.domain.TrackerId;
import sk.workingset.domain.WorkingSetId;

/** Tracker 聚合存取与删除前置查询：组织子级、派生子级、空间内 Tracker。 */
public interface TrackerRepository {
    Optional<Tracker> findTracker(TrackerId id);
    Optional<Tracker> lockTracker(TrackerId id);
    Optional<Tracker> findDerivedTracker(WorkingSetId target, TrackerId source);
    /** 只读列出工作集内的 Tracker，供界面构建 Tracker 树；包含尚未绑定的副本。 */
    List<Tracker> findTrackers(WorkingSetId workingSetId);
    void insertTracker(Tracker tracker);
    /** 扩展任务创建的新 Tracker 在任务终态前不可见。 */
    void insertInvisibleTracker(Tracker tracker);
    boolean isVisible(TrackerId trackerId);
    /** 终态后只揭示已成功建立绑定的新 Tracker。 */
    void revealExtensionTrackers(WorkingSetId target, List<TrackerId> parentTrackerIds);
    boolean hasOrgChildren(TrackerId id);
    boolean hasDerivedChildren(TrackerId id);
    boolean hasTrackersInSpace(String spaceId);
    void deleteTracker(TrackerId id);
}
