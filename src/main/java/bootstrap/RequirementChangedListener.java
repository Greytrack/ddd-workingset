package bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.workingset.application.event.RequirementChanged;

/**
 * RequirementChanged 的最小消费者（占位）：启用 Spring Modulith Event Publication Registry。
 *
 * <p>Modulith 会在监听器执行前把事件写入 {@code event_publication}；监听器成功返回后标记完成，
 * 抛异常则保留未完成记录，供 {@code IncompleteEventPublications} 重试。S3 将把这里替换为
 * CowEventDispatcher/COW 编排，不在本类中实现业务物化。
 */
@Component
public class RequirementChangedListener {

    private static final Logger log = LoggerFactory.getLogger(RequirementChangedListener.class);

    @ApplicationModuleListener
    public void on(RequirementChanged event) {
        log.info("RequirementChanged consumed: eventId={}, requirementId={}",
                event.eventId(), event.requirementId().value());
    }
}
