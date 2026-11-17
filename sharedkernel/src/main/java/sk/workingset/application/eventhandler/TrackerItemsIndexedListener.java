package sk.workingset.application.eventhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.workingset.application.event.TrackerItemsIndexed;

/**
 * 为索引完成事件保留 Modulith 发布记录。关系请求由 T7 任务的独立步骤发布。
 */
@Component
public class TrackerItemsIndexedListener {
    private static final Logger log = LoggerFactory.getLogger(TrackerItemsIndexedListener.class);

    @ApplicationModuleListener(id = "bootstrap.TrackerItemsIndexedListener.on(sk.workingset.application.event.TrackerItemsIndexed)")
    public void on(TrackerItemsIndexed event) {
        log.debug("Working set {} indexed trackers: {}", event.workingSetId(), event.trackerIds());
    }
}
