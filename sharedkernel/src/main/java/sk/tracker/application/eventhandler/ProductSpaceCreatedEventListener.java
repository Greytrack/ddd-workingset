package sk.tracker.application.eventhandler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.productspace.domain.event.ProductSpaceCreatedEvent;
import sk.tracker.application.command.DefaultTrackerInitCommandService;
import sk.tracker.domain.TrackerId;

import java.util.List;

/**
 * ProductSpace 创建后，在监听器事务内批量创建默认 Tracker 并写入绑定事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSpaceCreatedEventListener {
    private final DefaultTrackerInitCommandService trackerInitCommandService;

    @ApplicationModuleListener(id = "bootstrap.ProductSpaceProvisioningListener.on(sk.productspace.domain.event.ProductSpaceCreatedEvent)")
    public void on(ProductSpaceCreatedEvent event) {
        List<TrackerId> trackerIds = trackerInitCommandService.provisionAll(event.productSpaceId());
        log.info("=================>初始化默认tracker完成，共初始化{}条", trackerIds.size());
    }
}
