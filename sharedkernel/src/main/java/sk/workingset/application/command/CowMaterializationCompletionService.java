package sk.workingset.application.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

import org.springframework.transaction.support.TransactionTemplate;

import sk.workingset.application.query.provider.CowFailureLogQueryProvider;
import sk.workingset.domain.LightTrackerItem;
import sk.workingset.domain.event.TrackerItemMaterializationCompletedEvent;
import sk.workingset.domain.repository.CowMaterializationRepository;
import sk.workingset.domain.repository.LightTrackerItemRepository;

/** 消费 ALM 的实例化结果：成功则切换 LTI，失败则落失败终态。 */
@Slf4j
@Service
@RequiredArgsConstructor
public final class CowMaterializationCompletionService {
    private final CowMaterializationRepository store;
    private final LightTrackerItemRepository items;
    private final CowFailureLogQueryProvider failureLog;
    private final TransactionTemplate transaction;


    public void complete(TrackerItemMaterializationCompletedEvent event) {
        if (event.outcome() == TrackerItemMaterializationCompletedEvent.Outcome.MATERIALIZED) {
            transaction.executeWithoutResult(status -> switchToLocalBacked(event));
        } else {
            transaction.executeWithoutResult(status -> fail(event));
        }
    }

    private void switchToLocalBacked(TrackerItemMaterializationCompletedEvent event) {
        CowMaterializationRepository.Entry receipt = store.lockByEventAndTarget(event.eventId(), event.targetLocalId())
                .orElseThrow(() -> new IllegalStateException("COW receipt is missing"));
        if (receipt.status() == CowMaterializationRepository.Status.SWITCHED) {
            return;
        }
        store.markMaterialized(event.eventId(), event.targetLocalId());
        LightTrackerItem target = items.findById(event.targetLocalId())
                .orElseThrow(() -> new IllegalStateException("LightTrackerItem is missing"));
        if (!target.materialized() && (!target.sourceLocalId().equals(receipt.sourceLocalId())
                || !target.sourceRevision().equals(receipt.sourceRevision()))) {
            throw new IllegalStateException("LightTrackerItem no longer matches the receipt");
        }
        if (!target.materialized()) {
            target.switchToLocalBacked(target.revision());
            items.save(target);
        }
        store.markSwitched(event.eventId(), event.targetLocalId());
    }

    private void fail(TrackerItemMaterializationCompletedEvent event) {
        CowMaterializationRepository.Entry receipt = store.findByEventAndTarget(event.eventId(), event.targetLocalId())
                .orElse(null);
        store.markFailed(event.eventId(), event.targetLocalId(), event.failure());
        if (receipt != null) {
            failureLog.record(new CowFailureLogQueryProvider.Failure(event.eventId(), event.targetLocalId(),
                    receipt.sourceLocalId(), receipt.sourceRevision(), receipt.sourceRevision(),
                    event.beforeLockVersion(), event.afterLockVersion(), receipt.attempts(), "MATERIALIZE", "MATERIALIZATION_FAILED",
                    event.failure(), Instant.now()));
        }
    }
}
