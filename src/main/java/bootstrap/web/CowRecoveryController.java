package bootstrap.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import sk.workingset.application.CowRecoveryService;
import sk.workingset.application.port.MaterializationRejectedException;
import sk.workingset.domain.LocalId;

@RestController
@RequestMapping("/api/cow/recover")
public class CowRecoveryController {
    private final CowRecoveryService recovery;

    public CowRecoveryController(CowRecoveryService recovery) {
        this.recovery = recovery;
    }

    @PostMapping("/incomplete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void recoverIncomplete() {
        recovery.recoverIncompletePublications();
    }

    @PostMapping("/switch/{eventId}/{targetLocalId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recoverSwitch(@PathVariable long eventId, @PathVariable String targetLocalId) {
        try {
            recovery.recoverSwitchOnly(eventId, LocalId.of(targetLocalId));
        } catch (MaterializationRejectedException rejected) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, rejected.getMessage(), rejected);
        }
    }
}
