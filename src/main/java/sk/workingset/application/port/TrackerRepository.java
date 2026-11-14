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
    boolean hasOrgChildren(TrackerId id);
    boolean hasDerivedChildren(TrackerId id);
    boolean hasTrackersInSpace(String spaceId);
    void deleteTracker(TrackerId id);
}
