package sk.workingset.application.eventhandler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.workingset.application.event.TrackerItemsIndexed;

/**
 * T7-D 索引完成事件的持久化占位监听器。
 *
 * <p>Modulith 只为带 {@link ApplicationModuleListener} 的事件建立发布记录。T7-E 尚未接入
 * 关系构建；本监听器先让事件进入 {@code event_publication} 并可重投。T7-E 接入时替换为
 * 真实消费者，不要在此累积业务逻辑。
 */
@Component
public class TrackerItemsIndexedListener {
    private static final Logger log = LoggerFactory.getLogger(TrackerItemsIndexedListener.class);

    @ApplicationModuleListener(id = "bootstrap.TrackerItemsIndexedListener.on(sk.workingset.application.event.TrackerItemsIndexed)")
    public void on(TrackerItemsIndexed event) {
        log.debug("Working set {} indexed trackers: {}", event.workingSetId(), event.trackerIds());
    }
}
