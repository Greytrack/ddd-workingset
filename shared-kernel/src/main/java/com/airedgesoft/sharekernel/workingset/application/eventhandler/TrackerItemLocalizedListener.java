package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.sharekernel.workingset.application.command.TrackerItemCommandService;
import com.airedgesoft.sharekernel.workingset.domain.event.BusinessObjectUpdateEvent;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItem;
import com.airedgesoft.sharekernel.workingset.domain.lighttrackeritem.LightTrackerItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 业务对象普通修改提交后，SK 根据来源事件完成首次继承编辑的本地内容切换。
 *
 * <p>业务 BC 不再同步调用 SK 生命周期端口；事件在来源事务提交后处理。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackerItemLocalizedListener {

    private final LightTrackerItemRepository items;

    private final TrackerItemCommandService lifecycle;


    @ApplicationModuleListener(id = "bootstrap.TrackerItemLocalizedListener.on(sk.workingset.domain.event.BusinessObjectUpdateEvent)")
    public void on(BusinessObjectUpdateEvent event) {
        if (!event.beforeRevision().equals(event.afterRevision())) {
            lifecycle.applyCommittedUpgrade(event.sourceLocalId(), event.beforeRevision(), event.afterRevision());
            return;
        }
        LightTrackerItem item = items.findById(event.sourceLocalId()).orElse(null);
        if (item != null && !item.materialized()) {
            lifecycle.completeInheritedEdit(event.sourceLocalId(), event.afterRevision());
        }
    }
}
