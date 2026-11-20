package sk.tracker.application.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.productspace.domain.event.ProductSpaceCreatedEvent;
import sk.tracker.application.command.DefaultTrackerProvisioningService;

/** ProductSpace 创建后，在监听器事务内批量创建默认 Tracker 并写入绑定事件。 */
@Component
@RequiredArgsConstructor
public class ProductSpaceProvisioningListener {
    private final DefaultTrackerProvisioningService provisioning;

    @ApplicationModuleListener(id = "bootstrap.ProductSpaceProvisioningListener.on(sk.productspace.domain.event.ProductSpaceCreatedEvent)")
    public void on(ProductSpaceCreatedEvent event) {
        provisioning.provisionAll(event.productSpaceId());
    }
}
