package com.airedgesoft.sharekernel.productspace.application.eventhandler;

import com.airedgesoft.sharekernel.productspace.application.command.ProductSpaceCommandService;
import com.airedgesoft.sharekernel.productspace.domain.event.ProductSpaceDeletionRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductSpaceDeletionListener {
    private final ProductSpaceCommandService commands;

    @ApplicationModuleListener
    public void on(ProductSpaceDeletionRequested event) {
        commands.advanceDeletion(event);
    }
}
