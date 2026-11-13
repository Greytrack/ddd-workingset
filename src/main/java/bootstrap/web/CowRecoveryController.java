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

/**
 * COW 恢复与未完成事件重投入口。
 *
 * <p>可重试“已保存业务对象但未切换 LTI”的物化收据，并重投 Modulith 未完成事件
 * （RequirementChanged / RequirementPurged / RequirementMergeApplied）。
 */
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
