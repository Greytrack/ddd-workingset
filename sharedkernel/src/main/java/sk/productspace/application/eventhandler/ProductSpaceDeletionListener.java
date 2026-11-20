package sk.productspace.application.eventhandler;

import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import sk.productspace.application.command.ProductSpaceCommandService;
import sk.productspace.domain.event.ProductSpaceDeletionRequested;

@Component
@RequiredArgsConstructor
public class ProductSpaceDeletionListener {
    private final ProductSpaceCommandService commands;

    @ApplicationModuleListener
    public void on(ProductSpaceDeletionRequested event) {
        commands.advanceDeletion(event);
    }
}
