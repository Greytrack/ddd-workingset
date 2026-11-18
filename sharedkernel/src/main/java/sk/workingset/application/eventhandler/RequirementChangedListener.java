package sk.workingset.application.eventhandler;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import sk.workingset.domain.event.RequirementChanged;
import sk.workingset.application.command.CowEventHandler;
import sk.workingset.application.command.SourceSerialCowCoordinator;

/**
 * 在来源提交后执行 COW，并以处理结果决定 Modulith 发布记录是否完成。
 */
@Component
public class RequirementChangedListener {

    private final SourceSerialCowCoordinator coordinator;
    private final CowEventHandler handler;

    public RequirementChangedListener(SourceSerialCowCoordinator coordinator, CowEventHandler handler) {
        this.coordinator = coordinator;
        this.handler = handler;
    }

    @ApplicationModuleListener(id = "bootstrap.RequirementChangedListener.on(sk.workingset.domain.event.RequirementChanged)")
    public void on(RequirementChanged event) {
        coordinator.runSerial(event.sourceLocalId(), event.afterLockVersion().value(),
                () -> handler.onRequirementChanged(event));
    }
}
