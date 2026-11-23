package com.airedgesoft.sharekernel.workingset.application.eventhandler;

import com.airedgesoft.sharekernel.workingset.application.command.LightTrackerItemCommandService;
import com.airedgesoft.sharekernel.workingset.domain.event.BusinessObjectCheckedInEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.BusinessObjectCheckedOutEvent;
import com.airedgesoft.sharekernel.workingset.domain.event.BusinessObjectCheckoutCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TrackerItemCheckoutListener {

    private final LightTrackerItemCommandService lightTrackerItemCommandService;

    @ApplicationModuleListener
    public void on(BusinessObjectCheckedInEvent event) {
        lightTrackerItemCommandService.applyCheckin(event);
    }

    @ApplicationModuleListener
    public void on(BusinessObjectCheckedOutEvent event) {
        lightTrackerItemCommandService.applyCheckout(event);
    }

    @ApplicationModuleListener
    public void on(BusinessObjectCheckoutCancelledEvent event) {
        lightTrackerItemCommandService.applyCheckoutCancellation(event);
    }
}
