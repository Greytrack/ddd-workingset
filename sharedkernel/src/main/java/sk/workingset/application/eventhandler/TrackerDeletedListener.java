package sk.workingset.application.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.tracker.domain.repository.TrackerRepository;
import sk.workingset.domain.event.TrackerDeletedEvent;

/** 绑定清理完成后，Tracker 聚合执行最终物理删除。 */
@Component
@RequiredArgsConstructor
public class TrackerDeletedListener {
    private final TrackerRepository trackers;

    @ApplicationModuleListener(id = "bootstrap.TrackerDeletedListener.on(sk.workingset.domain.event.TrackerDeletedEvent)")
    public void on(TrackerDeletedEvent event) {
        trackers.deleteTracker(event.trackerId());
    }
}
