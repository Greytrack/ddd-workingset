package com.airedgesoft.sharekernel.tracker.application.eventhandler;

import com.airedgesoft.sharekernel.productspace.domain.event.ProductSpaceCreatedEvent;
import com.airedgesoft.sharekernel.tracker.application.command.DefaultTrackerInitCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ProductSpace 创建后逐个提交默认 Tracker，再登记绑定事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSpaceCreatedEventListener {
    private final DefaultTrackerInitCommandService trackerInitCommandService;

    @ApplicationModuleListener(id = "bootstrap.ProductSpaceProvisioningListener.on(sk.productspace.domain.event.ProductSpaceCreatedEvent)")
    public void on(ProductSpaceCreatedEvent event) {
        List<Long> trackerIds = trackerInitCommandService.provisionAll(event.productSpaceId());
        log.info("=================>初始化默认tracker完成，共初始化{}条", trackerIds.size());
    }
}
