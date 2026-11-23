package com.airedgesoft.alm.requirement.application.eventhandler;

import com.airedgesoft.sharedkernel.workingset.domain.event.TrackerItemsIndexedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 派生需求的公共查询视图投影入口。
 *
 * <p>{@code air_inst_common} 是公共查询视图/缓存，不是业务权威；编号、名称、状态等共性字段
 * 以及写入规则由 ALM 自己决定。SK 在 LightTrackerItem 索引完成后发布事件，不再直接写该表。
 *
 * <p>当前只登记事件并保留空实现；具体投影规则待定。
 */
@Slf4j
@Component
public class RequirementDerivedItemSummaryListener {

    @ApplicationModuleListener(id = "bootstrap.RequirementDerivedItemSummaryListener.on(sk.workingset.domain.event.TrackerItemsIndexedEvent)")
    public void on(TrackerItemsIndexedEvent event) {
        log.debug("Requirement public summary projection deferred: workingSet={}, trackers={}",
                event.workingSetId(), event.trackerIds());
    }
}
