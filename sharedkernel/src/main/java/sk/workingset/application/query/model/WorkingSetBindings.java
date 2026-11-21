package sk.workingset.application.query.model;

import java.util.List;

/**
 * Working Set 绑定读模型。
 */
public record WorkingSetBindings(List<SpaceBindingSummary> spaces, List<TrackerBindingSummary> trackers) {
}
